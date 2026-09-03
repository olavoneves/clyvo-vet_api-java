package br.com.clyvovet.server.painel;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Painel de receita recuperada.
 *
 * <p>Os dois blocos tem recortes deliberadamente diferentes:
 *
 * <ul>
 *   <li>O <b>funil</b> e mensal. Ele mostra volume, e volume se le por mes.
 *   <li>A <b>comparacao</b> tratado vs. controle agrega a janela historica
 *       inteira. Num unico mes o grupo de controle tem poucas dezenas de
 *       observacoes — uma ou duas obrigacoes cumpridas a mais deslocam a taxa
 *       em dezenas de pontos, e o delta nao sobrevive a escrutinio.
 * </ul>
 *
 * <p>Tratado e o grupo que recebeu lembrete; controle e o sorteado para ficar de
 * fora. A diferenca entre os dois e a unica forma honesta de dizer quanto da
 * receita o produto recuperou, e nao teria acontecido de qualquer jeito.
 */
public record PainelReceitaResponse(
        LocalDate mesReferencia,
        Funil funil,
        BigDecimal vlRecuperadoTotal,
        BigDecimal vlPerdidoTotal,
        Comparacao comparacao
) {

    /** Etapas do funil, do compromisso previsto ao comparecimento. Recorte mensal. */
    public record Funil(
            long obrigacoes,
            long notificadas,
            long respondidas,
            long agendadas,
            long cumpridas,
            long perdidas
    ) {}

    /**
     * Tratado vs. controle sobre toda a janela disponivel.
     *
     * <p>Carrega o tamanho dos dois grupos de proposito: um delta acompanhado de
     * base amostral e defensavel, um percentual solto nao e. Quem le a tela
     * precisa poder julgar se o numero se sustenta.
     */
    public record Comparacao(
            LocalDate janelaInicio,
            LocalDate janelaFim,
            Grupo tratado,
            Grupo controle,
            /** Tratado menos controle, em pontos percentuais. Nulo se faltar um grupo. */
            BigDecimal deltaPontosPercentuais
    ) {}

    public record Grupo(
            long obrigacoes,
            long cumpridas,
            BigDecimal pcComparecimento,
            BigDecimal vlRecuperado,
            BigDecimal vlPerdido
    ) {}
}
