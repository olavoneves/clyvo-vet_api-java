package br.com.clyvovet.server.painel.web;

import br.com.clyvovet.server.painel.PainelReceitaResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um par de barras: taxa de comparecimento do tratado e a do controle, sobre a
 * janela historica inteira.
 *
 * <p>Leva junto o tamanho do grupo. O grafico usa so a taxa, mas quem monta a
 * legenda precisa da base amostral do lado — taxa sem denominador ao lado e
 * exatamente o tipo de numero que nao se sustenta numa pergunta.
 */
public record ComparacaoView(String rotulo, BigDecimal taxa, long obrigacoes) {

    public static List<ComparacaoView> de(PainelReceitaResponse painel) {
        var comparacao = painel.comparacao();
        return List.of(
                new ComparacaoView("Tratado", comparacao.tratado().pcComparecimento(),
                        comparacao.tratado().obrigacoes()),
                new ComparacaoView("Controle", comparacao.controle().pcComparecimento(),
                        comparacao.controle().obrigacoes()));
    }
}
