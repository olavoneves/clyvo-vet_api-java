package br.com.clyvovet.server.painel;

import br.com.clyvovet.server.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * O card de coorte: tratado contra controle, e o quanto isso vale em reais.
 *
 * <p>Serviço próprio, e não mais um método no {@link PainelReceitaService},
 * porque lê outra view com outro recorte. Misturar os dois no mesmo serviço
 * convidaria a somar números de denominadores diferentes — que é exatamente o
 * erro que este card existe para não cometer.
 *
 * <p>O recorte por clínica sai do {@code TenantContext}, nunca de parâmetro da
 * requisição: mesma regra do painel de receita.
 */
@Service
@RequiredArgsConstructor
public class PainelCoorteService {

    private static final String SQL = """
            select ds_grupo, qt_obrigacoes, qt_cumpridas, pc_cumprimento,
                   qt_pets, vl_ticket_medio
              from VW_CLV_PAINEL_COORTE
             where id_clinica = :idClinica
            """;

    private static final String TRATADO = "TRATADO";
    private static final String CONTROLE = "CONTROLE";

    private final JdbcClient jdbcClient;

    @Transactional(readOnly = true)
    public CoorteResponse daClinicaLogada() {
        List<Linha> linhas = jdbcClient.sql(SQL)
                .param("idClinica", TenantContext.getOrThrow())
                .query(Linha.class)
                .list();

        Linha tratado = doGrupo(linhas, TRATADO);
        Linha controle = doGrupo(linhas, CONTROLE);

        BigDecimal delta = delta(tratado, controle);
        boolean suficiente = tratado != null && controle != null
                && controle.qtPets() >= CoorteResponse.MINIMO_DE_PETS_NO_CONTROLE;

        // sem amostra o delta nao vira dinheiro: um numero em reais tirado de um
        // punhado de pets e mais perigoso que numero nenhum, porque parece medido
        long consultas = suficiente ? consultasAtribuiveis(tratado, delta) : 0;
        BigDecimal ticket = ticketMedio(tratado, controle);

        return new CoorteResponse(
                paraGrupo(tratado),
                paraGrupo(controle),
                delta,
                consultas,
                suficiente ? ticket.multiply(BigDecimal.valueOf(consultas)) : BigDecimal.ZERO,
                ticket,
                suficiente);
    }

    /**
     * As consultas que existem por causa do produto.
     *
     * <p>Obrigações do grupo tratado vezes o delta: se o tratado cumpre 59% e o
     * controle 33%, os 26 pontos de diferença são o que o produto acrescentou, e
     * aplicá-los à base tratada dá o número de consultas que não teriam
     * acontecido sem ele. Delta negativo vira zero — o produto não pode reivindicar
     * dano como se fosse ganho, e um número negativo aqui é sinal de que a amostra
     * ainda não presta.
     */
    private long consultasAtribuiveis(Linha tratado, BigDecimal delta) {
        if (delta == null || delta.signum() <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(tratado.qtObrigacoes())
                .multiply(delta)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    /** O ticket já vem da view, repetido nas duas linhas: qualquer uma serve. */
    private BigDecimal ticketMedio(Linha tratado, Linha controle) {
        Linha qualquer = tratado != null ? tratado : controle;
        if (qualquer == null || qualquer.vlTicketMedio() == null) {
            return BigDecimal.ZERO;
        }
        return qualquer.vlTicketMedio();
    }

    private BigDecimal delta(Linha tratado, Linha controle) {
        if (tratado == null || controle == null
                || tratado.pcCumprimento() == null || controle.pcCumprimento() == null) {
            return null;
        }
        return tratado.pcCumprimento().subtract(controle.pcCumprimento());
    }

    private Linha doGrupo(List<Linha> linhas, String grupo) {
        return linhas.stream()
                .filter(l -> grupo.equalsIgnoreCase(l.dsGrupo()))
                .findFirst()
                .orElse(null);
    }

    private CoorteResponse.Grupo paraGrupo(Linha linha) {
        if (linha == null) {
            return CoorteResponse.Grupo.vazio();
        }
        return new CoorteResponse.Grupo(linha.qtObrigacoes(), linha.qtCumpridas(),
                linha.qtPets(), linha.pcCumprimento());
    }

    /** Uma linha de VW_CLV_PAINEL_COORTE. */
    public record Linha(String dsGrupo, long qtObrigacoes, long qtCumpridas,
                        BigDecimal pcCumprimento, long qtPets, BigDecimal vlTicketMedio) {
    }
}
