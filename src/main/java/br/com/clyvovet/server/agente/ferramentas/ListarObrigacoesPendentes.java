package br.com.clyvovet.server.agente.ferramentas;

import br.com.clyvovet.server.agente.ContextoDoAgente;
import br.com.clyvovet.server.agente.Esquemas;
import br.com.clyvovet.server.agente.Ferramenta;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.obrigacao.ObrigacaoResponse;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O que este pet ainda deve. E a primeira coisa que o agente precisa saber para
 * conversar sobre marcar qualquer coisa.
 */
@Component
@RequiredArgsConstructor
public class ListarObrigacoesPendentes implements Ferramenta {

    /**
     * Pendente e tudo que ainda pode virar agendamento.
     *
     * <p>AGENDADA fica de fora de proposito: ja tem horario marcado, e ofere-la
     * de novo faria o agente propor um segundo agendamento para o mesmo
     * compromisso.
     */
    private static final Set<ObrigacaoStatus> PENDENTES = Set.of(
            ObrigacaoStatus.PREVISTA, ObrigacaoStatus.NOTIFICADA, ObrigacaoStatus.RESPONDIDA);

    private static final Pageable POR_DATA =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "dtPrevista"));

    private final ObrigacaoService obrigacaoService;

    /** So o que o modelo precisa ler; o resto da obrigacao nao ajuda a marcar horario. */
    private record Pendencia(Long idObrigacao, String etapa, String protocolo, String categoria,
                             LocalDate dataPrevista, LocalDate janelaInicio, LocalDate janelaFim,
                             String situacao) {
    }

    @Override
    public String nome() {
        return "listar_obrigacoes_pendentes";
    }

    @Override
    public String descricao() {
        return """
                Lista os compromissos de cuidado que ainda estão em aberto para o pet: \
                vacinas, vermifugações, retornos e exames que o protocolo clínico previu \
                e que ainda não têm horário marcado. Cada item traz o id da obrigação, \
                a data prevista e a janela aceitável para realizá-la. \
                Use sempre esta ferramenta antes de propor horários, para saber sobre o que \
                a conversa é e qual id usar ao agendar.""";
    }

    @Override
    public Map<String, Object> esquema() {
        return Esquemas.objeto(Esquemas.inteiro("idPet",
                "Id do pet da conversa. Deve ser exatamente o pet informado no contexto."));
    }

    @Override
    public Resultado executar(Map<String, Object> entrada, ContextoDoAgente contexto) {
        FerramentaGuardas.exigirPetDaConversa(Esquemas.inteiroDe(entrada, "idPet"), contexto);

        List<Pendencia> pendencias = obrigacaoService
                .doPetComStatus(contexto.idPet(), PENDENTES, POR_DATA).stream()
                .map(ListarObrigacoesPendentes::resumir)
                .toList();

        return Resultado.leitura(Map.of(
                "idPet", contexto.idPet(),
                "quantidade", pendencias.size(),
                "pendencias", pendencias));
    }

    private static Pendencia resumir(ObrigacaoResponse o) {
        return new Pendencia(o.id(), o.etapaNome(), o.protocoloNome(),
                o.protocoloCategoria() == null ? null : o.protocoloCategoria().name(),
                o.dtPrevista(), o.dtJanelaInicio(), o.dtJanelaFim(), o.status().name());
    }
}
