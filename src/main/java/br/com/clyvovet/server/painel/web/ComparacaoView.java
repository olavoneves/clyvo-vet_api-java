package br.com.clyvovet.server.painel.web;

import br.com.clyvovet.server.painel.PainelReceitaResponse;

import java.math.BigDecimal;
import java.util.List;

/** Um par de barras: taxa de comparecimento do grupo tratado e a do controle. */
public record ComparacaoView(String rotulo, BigDecimal taxa) {

    public static List<ComparacaoView> de(PainelReceitaResponse painel) {
        return List.of(
                new ComparacaoView("Tratado", painel.tratado().pcComparecimento()),
                new ComparacaoView("Controle", painel.controle().pcComparecimento()));
    }
}
