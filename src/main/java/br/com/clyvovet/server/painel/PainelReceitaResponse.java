package br.com.clyvovet.server.painel;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Painel de receita recuperada do mes.
 *
 * <p>Os numeros vem prontos de VW_CLV_PAINEL_RECEITA, que entrega uma linha por
 * grupo. Tratado e o grupo que recebeu lembrete; controle e o sorteado para
 * ficar de fora. A diferenca entre os dois e a unica forma honesta de dizer
 * quanto da receita o produto recuperou, e nao teria acontecido de qualquer jeito.
 */
public record PainelReceitaResponse(
        LocalDate mesReferencia,
        Funil funil,
        BigDecimal vlRecuperadoTotal,
        BigDecimal vlPerdidoTotal,
        Grupo tratado,
        Grupo controle,
        /** Tratado menos controle, em pontos percentuais. Nulo se faltar um dos grupos. */
        BigDecimal lift
) {

    /** Etapas do funil, do compromisso previsto ao comparecimento. */
    public record Funil(
            long obrigacoes,
            long notificadas,
            long respondidas,
            long agendadas,
            long cumpridas,
            long perdidas
    ) {}

    public record Grupo(
            long obrigacoes,
            long cumpridas,
            BigDecimal pcComparecimento,
            BigDecimal vlRecuperado,
            BigDecimal vlPerdido
    ) {}
}
