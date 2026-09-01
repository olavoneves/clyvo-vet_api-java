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

/**
 * Le VW_CLV_PAINEL_RECEITA.
 *
 * <p>A view nao e entidade JPA, entao o filtro de tenant do Hibernate nao a
 * alcanca: o recorte por clinica e feito aqui, explicitamente, a partir do
 * TenantContext. Nunca a partir de parametro da requisicao.
 */
@Service
@RequiredArgsConstructor
public class PainelReceitaService {

    private static final String SQL = """
            select fl_grupo_controle, qt_obrigacoes, qt_notificadas, qt_respondidas,
                   qt_agendadas, qt_cumpridas, qt_perdidas, pc_comparecimento,
                   vl_recuperado, vl_perdido
              from VW_CLV_PAINEL_RECEITA
             where id_clinica = :idClinica
               and mes_referencia = :mes
            """;

    private final JdbcClient jdbcClient;

    @Transactional(readOnly = true)
    public PainelReceitaResponse doMes(LocalDate mes) {
        LocalDate primeiroDia = mes.withDayOfMonth(1);

        List<LinhaPainel> linhas = jdbcClient.sql(SQL)
                .param("idClinica", TenantContext.getOrThrow())
                .param("mes", Date.valueOf(primeiroDia))
                .query(LinhaPainel.class)
                .list();

        LinhaPainel tratado = linhaDoGrupo(linhas, false);
        LinhaPainel controle = linhaDoGrupo(linhas, true);

        return new PainelReceitaResponse(
                primeiroDia,
                somarFunil(linhas),
                somar(linhas, LinhaPainel::vlRecuperado),
                somar(linhas, LinhaPainel::vlPerdido),
                paraGrupo(tratado),
                paraGrupo(controle),
                calcularLift(tratado, controle));
    }

    private LinhaPainel linhaDoGrupo(List<LinhaPainel> linhas, boolean grupoControle) {
        String marcador = grupoControle ? "S" : "N";
        return linhas.stream()
                .filter(l -> marcador.equalsIgnoreCase(l.flGrupoControle()))
                .findFirst()
                .orElse(null);
    }

    private PainelReceitaResponse.Funil somarFunil(List<LinhaPainel> linhas) {
        return new PainelReceitaResponse.Funil(
                linhas.stream().mapToLong(LinhaPainel::qtObrigacoes).sum(),
                linhas.stream().mapToLong(LinhaPainel::qtNotificadas).sum(),
                linhas.stream().mapToLong(LinhaPainel::qtRespondidas).sum(),
                linhas.stream().mapToLong(LinhaPainel::qtAgendadas).sum(),
                linhas.stream().mapToLong(LinhaPainel::qtCumpridas).sum(),
                linhas.stream().mapToLong(LinhaPainel::qtPerdidas).sum());
    }

    private PainelReceitaResponse.Grupo paraGrupo(LinhaPainel linha) {
        if (linha == null) {
            return new PainelReceitaResponse.Grupo(0, 0, null, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new PainelReceitaResponse.Grupo(
                linha.qtObrigacoes(), linha.qtCumpridas(), linha.pcComparecimento(),
                zeroSeNulo(linha.vlRecuperado()), zeroSeNulo(linha.vlPerdido()));
    }

    private BigDecimal calcularLift(LinhaPainel tratado, LinhaPainel controle) {
        if (tratado == null || controle == null
                || tratado.pcComparecimento() == null || controle.pcComparecimento() == null) {
            return null;
        }
        return tratado.pcComparecimento()
                .subtract(controle.pcComparecimento())
                .setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal somar(List<LinhaPainel> linhas,
                             java.util.function.Function<LinhaPainel, BigDecimal> campo) {
        return linhas.stream()
                .map(campo)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    /** Uma linha da view: sempre um grupo de um mes de uma clinica. */
    public record LinhaPainel(
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
}
