package br.com.clyvovet.server.atendimento;

import br.com.clyvovet.server.enums.EventoGatilho;
import br.com.clyvovet.server.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Os contextos que o motor de fato le, por gatilho, tirados do catalogo.
 *
 * <p>Existe porque o campo era texto livre, e texto livre e inutilizavel: o
 * motor so reconhece um codigo exato da lista de uma condicao
 * {@code PROCEDIMENTO}, e quem digitava "castracao" via zero obrigacoes sem
 * entender por que. A tela agora oferece exatamente os codigos que as regras
 * de ativacao daquele gatilho aceitam.
 *
 * <p><b>Nenhum codigo mora no Java.</b> A lista sai de
 * {@code TB_CLV_CONDICAO_PROT} pelo mesmo recorte que
 * {@code PKG_CLV_MOTOR.GERAR_OBRIGACOES} usa para escolher as regras — versao
 * VIGENTE, protocolo ativo, global ou da clinica — e se parte na virgula como o
 * {@code AVALIA_CONDICAO} parte. Protocolo novo no catalogo aparece na tela sem
 * deploy.
 *
 * <p>O rotulo vem do nome da etapa que executa aquele procedimento
 * ({@code TB_CLV_ETAPA_PROTOCOLO.ds_procedimento}). Codigo sem etapa — o motor
 * aceita, mas nenhum protocolo o agenda — aparece pelo proprio codigo.
 */
@Service
@RequiredArgsConstructor
public class CatalogoDeContextos {

    private final EntityManager entityManager;

    /** Um contexto que o usuario pode escolher. */
    public record OpcaoDeContexto(String codigo, String rotulo) {
    }

    /**
     * Os contextos aceitos por gatilho, na ordem em que o catalogo os lista.
     * Gatilho que nao le contexto nao aparece no mapa.
     */
    @Transactional(readOnly = true)
    public Map<EventoGatilho, List<OpcaoDeContexto>> porGatilho() {
        @SuppressWarnings("unchecked")
        List<Object[]> condicoes = entityManager.createNativeQuery("""
                        select r.ds_evento_gatilho, c.ds_valor_texto
                          from TB_CLV_CONDICAO_PROT c
                          join TB_CLV_REGRA_ATIVACAO r   on r.id_regra_ativacao   = c.id_regra_ativacao
                          join TB_CLV_VERSAO_PROTOCOLO v on v.id_versao_protocolo = r.id_versao_protocolo
                          join TB_CLV_PROTOCOLO p        on p.id_protocolo        = v.id_protocolo
                         where c.ds_atributo = 'PROCEDIMENTO'
                           and c.ds_valor_texto is not null
                           and v.ds_status = 'VIGENTE'
                           and p.fl_ativo  = 'S'
                           and (p.id_clinica is null or p.id_clinica = :clinica)
                         order by r.nr_prioridade, c.id_condicao_prot
                        """)
                // o protocolo e catalogo, sem filtro de tenant no Hibernate: o
                // recorte por clinica vai explicito, como no motor
                .setParameter("clinica", TenantContext.getOrSemTenant())
                .getResultList();

        Map<EventoGatilho, Set<String>> codigos = new EnumMap<>(EventoGatilho.class);
        for (Object[] linha : condicoes) {
            EventoGatilho gatilho;
            try {
                gatilho = EventoGatilho.valueOf((String) linha[0]);
            } catch (IllegalArgumentException desconhecido) {
                continue; // gatilho do banco que a aplicacao ainda nao oferece
            }
            Set<String> doGatilho = codigos.computeIfAbsent(gatilho, g -> new LinkedHashSet<>());
            for (String codigo : ((String) linha[1]).split(",")) {
                if (!codigo.isBlank()) {
                    doGatilho.add(codigo.trim().toUpperCase());
                }
            }
        }

        Map<String, String> rotulos = rotulos(codigos.values().stream().flatMap(Set::stream).toList());

        Map<EventoGatilho, List<OpcaoDeContexto>> resultado = new EnumMap<>(EventoGatilho.class);
        codigos.forEach((gatilho, doGatilho) -> {
            List<OpcaoDeContexto> opcoes = new ArrayList<>();
            for (String codigo : doGatilho) {
                String nome = rotulos.get(codigo);
                opcoes.add(new OpcaoDeContexto(codigo, nome == null ? codigo : nome + " (" + codigo + ")"));
            }
            resultado.put(gatilho, opcoes);
        });
        return resultado;
    }

    /** O contexto escolhido e um dos que o gatilho aceita no catalogo dado — ou nenhum. */
    public static boolean aceita(Map<EventoGatilho, List<OpcaoDeContexto>> catalogo,
                                 EventoGatilho gatilho, String contexto) {
        if (contexto == null || contexto.isBlank()) {
            return true;
        }
        return catalogo.getOrDefault(gatilho, List.of()).stream()
                .anyMatch(opcao -> opcao.codigo().equalsIgnoreCase(contexto.trim()));
    }

    private Map<String, String> rotulos(List<String> codigos) {
        Map<String, String> rotulos = new HashMap<>();
        if (codigos.isEmpty()) {
            return rotulos;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> etapas = entityManager.createNativeQuery("""
                        select upper(e.ds_procedimento), min(e.nm_etapa)
                          from TB_CLV_ETAPA_PROTOCOLO e
                         where upper(e.ds_procedimento) in (:codigos)
                         group by upper(e.ds_procedimento)
                        """)
                .setParameter("codigos", codigos)
                .getResultList();
        for (Object[] etapa : etapas) {
            rotulos.put((String) etapa[0], (String) etapa[1]);
        }
        return rotulos;
    }
}
