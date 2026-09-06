package br.com.clyvovet.server.painel.web;

import br.com.clyvovet.server.painel.CoorteResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um par de barras: taxa de cumprimento do tratado e a do controle.
 *
 * <p>Sai da <b>coorte</b>, e nao mais de {@code PainelReceitaResponse.Comparacao}.
 * As duas fontes respondem a mesma pergunta com denominadores diferentes — uma
 * conta toda obrigacao do mes, inclusive a que nem venceu; a outra so as que ja
 * se resolveram. Enquanto o grafico lia uma e o card lia a outra, a mesma pagina
 * exibia duas taxas diferentes para a mesma coisa, e quem perguntasse qual vale
 * ficaria sem resposta.
 *
 * <p>Leva junto o tamanho do grupo. O grafico usa so a taxa, mas quem monta a
 * legenda precisa da base amostral do lado — taxa sem denominador ao lado e
 * exatamente o tipo de numero que nao se sustenta numa pergunta.
 */
public record ComparacaoView(String rotulo, BigDecimal taxa, long obrigacoes) {

    public static List<ComparacaoView> de(CoorteResponse coorte) {
        return List.of(
                new ComparacaoView("Tratado", coorte.tratado().pcCumprimento(),
                        coorte.tratado().obrigacoes()),
                new ComparacaoView("Controle", coorte.controle().pcCumprimento(),
                        coorte.controle().obrigacoes()));
    }
}
