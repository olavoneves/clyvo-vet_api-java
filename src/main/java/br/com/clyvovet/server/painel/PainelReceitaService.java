package br.com.clyvovet.server.painel;

import br.com.clyvovet.server.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * Le VW_CLV_PAINEL_RECEITA em dois recortes.
 *
 * <p>A view entrega uma linha por clinica, mes e grupo. O funil consome o mes
 * pedido; a comparacao tratado vs. controle soma todos os meses disponiveis,
 * porque num unico mes o grupo de controle e pequeno demais para sustentar o
 * delta.
 *
 * <p>A view nao e entidade JPA, entao o filtro de tenant do Hibernate nao a
 * alcanca: o recorte por clinica e feito aqui, explicitamente, a partir do
 * TenantContext. Nunca a partir de parametro da requisicao.
 */
@Service
@RequiredArgsConstructor
public class PainelReceitaService {

    private static final String SQL_DO_MES = """
            select fl_grupo_controle, qt_obrigacoes, qt_notificadas, qt_respondidas,
                   qt_agendadas, qt_cumpridas, qt_perdidas, pc_comparecimento,
                   vl_recuperado, vl_perdido
              from VW_CLV_PAINEL_RECEITA
             where id_clinica = :idClinica
               and mes_referencia = :mes
            """;

    /**
     * Mesma view, sem filtro de mes.
     *
     * <p>pc_comparecimento nao entra na soma: percentual de percentual nao e
     * percentual. A taxa da janela e recalculada em Java a partir dos totais.
     */
    private static final String SQL_DA_JANELA = """
            select fl_grupo_controle,
                   sum(qt_obrigacoes) as qt_obrigacoes,
                   sum(qt_cumpridas)  as qt_cumpridas,
                   sum(vl_recuperado) as vl_recuperado,
                   sum(vl_perdido)    as vl_perdido,
                   min(mes_referencia) as mes_inicio,
                   max(mes_referencia) as mes_fim
              from VW_CLV_PAINEL_RECEITA
             where id_clinica = :idClinica
             group by fl_grupo_controle
            """;

    /** Marcador de grupo de controle na view: 'S' controle, 'N' tratado. */
    private static final String CONTROLE = "S";
    private static final String TRATADO = "N";

    private final JdbcClient jdbcClient;

    @Transactional(readOnly = true)
    public PainelReceitaResponse doMes(LocalDate mes) {
        LocalDate primeiroDia = mes.withDayOfMonth(1);
        Long idClinica = TenantContext.getOrThrow();

        List<LinhaDoMes> doMes = jdbcClient.sql(SQL_DO_MES)
                .param("idClinica", idClinica)
                .param("mes", Date.valueOf(primeiroDia))
                .query(LinhaDoMes.class)
                .list();

        List<LinhaDaJanela> daJanela = jdbcClient.sql(SQL_DA_JANELA)
                .param("idClinica", idClinica)
                .query(LinhaDaJanela.class)
                .list();

        return new PainelReceitaResponse(
                primeiroDia,
                somarFunil(doMes),
                somar(doMes, LinhaDoMes::vlRecuperado),
                somar(doMes, LinhaDoMes::vlPerdido),
                comparar(daJanela));
    }

    private PainelReceitaResponse.Funil somarFunil(List<LinhaDoMes> linhas) {
        return new PainelReceitaResponse.Funil(
                linhas.stream().mapToLong(LinhaDoMes::qtObrigacoes).sum(),
                linhas.stream().mapToLong(LinhaDoMes::qtNotificadas).sum(),
                linhas.stream().mapToLong(LinhaDoMes::qtRespondidas).sum(),
                linhas.stream().mapToLong(LinhaDoMes::qtAgendadas).sum(),
                linhas.stream().mapToLong(LinhaDoMes::qtCumpridas).sum(),
                linhas.stream().mapToLong(LinhaDoMes::qtPerdidas).sum());
    }

    private PainelReceitaResponse.Comparacao comparar(List<LinhaDaJanela> linhas) {
        LinhaDaJanela tratado = doGrupo(linhas, TRATADO);
        LinhaDaJanela controle = doGrupo(linhas, CONTROLE);

        return new PainelReceitaResponse.Comparacao(
                extremo(linhas, LinhaDaJanela::mesInicio, true),
                extremo(linhas, LinhaDaJanela::mesFim, false),
                paraGrupo(tratado),
                paraGrupo(controle),
                delta(tratado, controle));
    }

    private LinhaDaJanela doGrupo(List<LinhaDaJanela> linhas, String marcador) {
        return linhas.stream()
                .filter(l -> marcador.equalsIgnoreCase(l.flGrupoControle()))
                .findFirst()
                .orElse(null);
    }

    private PainelReceitaResponse.Grupo paraGrupo(LinhaDaJanela linha) {
        if (linha == null) {
            return new PainelReceitaResponse.Grupo(0, 0, null, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new PainelReceitaResponse.Grupo(
                linha.qtObrigacoes(), linha.qtCumpridas(), taxa(linha),
                zeroSeNulo(linha.vlRecuperado()), zeroSeNulo(linha.vlPerdido()));
    }

    /** Cumpridas sobre obrigacoes da janela inteira, nao a media das taxas mensais. */
    private BigDecimal taxa(LinhaDaJanela linha) {
        if (linha == null || linha.qtObrigacoes() == 0) {
            return null;
        }
        return BigDecimal.valueOf(linha.qtCumpridas())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(linha.qtObrigacoes()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal delta(LinhaDaJanela tratado, LinhaDaJanela controle) {
        BigDecimal taxaTratado = taxa(tratado);
        BigDecimal taxaControle = taxa(controle);
        if (taxaTratado == null || taxaControle == null) {
            return null;
        }
        return taxaTratado.subtract(taxaControle).setScale(1, RoundingMode.HALF_UP);
    }

    private LocalDate extremo(List<LinhaDaJanela> linhas,
                              Function<LinhaDaJanela, LocalDate> campo, boolean menor) {
        return linhas.stream()
                .map(campo)
                .filter(java.util.Objects::nonNull)
                .reduce((a, b) -> (menor == a.isBefore(b)) ? a : b)
                .orElse(null);
    }

    private BigDecimal somar(List<LinhaDoMes> linhas, Function<LinhaDoMes, BigDecimal> campo) {
        return linhas.stream()
                .map(campo)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    /** Uma linha da view: um grupo, de um mes, de uma clinica. */
    public record LinhaDoMes(
            String flGrupoControle,
            long qtObrigacoes,
            long qtNotificadas,
            long qtRespondidas,
            long qtAgendadas,
            long qtCumpridas,
            long qtPerdidas,
            BigDecimal pcComparecimento,
            BigDecimal vlRecuperado,
            BigDecimal vlPerdido
    ) {}

    /** Um grupo somado sobre todos os meses disponiveis. */
    public record LinhaDaJanela(
            String flGrupoControle,
            long qtObrigacoes,
            long qtCumpridas,
            BigDecimal vlRecuperado,
            BigDecimal vlPerdido,
            LocalDate mesInicio,
            LocalDate mesFim
    ) {}
}
