package br.com.clyvovet.server.painel.web;

import br.com.clyvovet.server.painel.PainelReceitaResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * O funil do painel na forma que a tela precisa: rotulo, valor absoluto e
 * quanto sobrou do topo.
 *
 * <p>A taxa nao vem da view do banco porque ali cada etapa e uma coluna solta;
 * a leitura de funil e a razao entre etapas, e ela so existe depois de escolher
 * qual e o topo. Isso e decisao de apresentacao, entao mora aqui e nao no
 * {@code PainelReceitaService} — a API continua entregando os numeros crus.
 */
public record FunilView(String rotulo, long valor, BigDecimal taxa) {

    /** Etapas na ordem em que a clinica as percorre. */
    public static List<FunilView> de(PainelReceitaResponse.Funil funil) {
        long topo = funil.obrigacoes();
        return List.of(
                etapa("Vencidas", funil.obrigacoes(), topo),
                etapa("Lembretes", funil.notificadas(), topo),
                etapa("Respostas", funil.respondidas(), topo),
                etapa("Agendamentos", funil.agendadas(), topo),
                etapa("Comparecimentos", funil.cumpridas(), topo));
    }

    /** Sem obrigacoes no topo nao ha percentual a calcular — e nao e zero, e nada. */
    private static FunilView etapa(String rotulo, long valor, long topo) {
        BigDecimal taxa = topo == 0
                ? null
                : BigDecimal.valueOf(valor)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(topo), 1, RoundingMode.HALF_UP);
        return new FunilView(rotulo, valor, taxa);
    }
}
