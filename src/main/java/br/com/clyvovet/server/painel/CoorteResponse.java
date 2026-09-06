package br.com.clyvovet.server.painel;

import java.math.BigDecimal;

/**
 * O experimento do produto reduzido ao numero que se defende numa banca: quanto
 * do comparecimento existe por causa do Clyvo, e quanto isso vale.
 *
 * <p>Nao e o mesmo recorte de {@link PainelReceitaResponse.Comparacao}, que
 * conta toda obrigacao do mes, inclusive a que ainda nem venceu. Aqui o
 * denominador sao so as obrigacoes ja resolvidas — e por isso as taxas daqui sao
 * as taxas de verdade do sorteio, e nao um numero que piora sozinho a cada mes
 * que passa.
 *
 * @param amostraSuficiente se o grupo de controle tem pets bastante para o delta
 *                          significar alguma coisa; quando falso, a tela mostra
 *                          a ressalva no lugar do numero
 */
public record CoorteResponse(
        Grupo tratado,
        Grupo controle,
        /** Tratado menos controle, em pontos percentuais. Nulo se faltar um grupo. */
        BigDecimal deltaPontosPercentuais,
        /** Consultas que existem porque o produto existe. Zero sem amostra. */
        long consultasAtribuiveis,
        /** As consultas atribuiveis ao ticket medio real da clinica. */
        BigDecimal vlAtribuivel,
        BigDecimal vlTicketMedio,
        boolean amostraSuficiente
) {

    /**
     * Piso de pets no grupo de controle para o delta ir para a tela.
     *
     * <p>Dez, e contado em <b>pets</b> e nao em obrigacoes, porque o sorteio e
     * por pet: as obrigacoes de um mesmo animal sobem e descem juntas, entao
     * trezentas obrigacoes de um pet so continuam sendo uma amostra de tamanho
     * um. Abaixo disso um unico tutor faltoso move a taxa em dezenas de pontos,
     * e ja aconteceu — com um pet no controle a base local dizia que o produto
     * derrubava o comparecimento em vinte pontos.
     */
    public static final int MINIMO_DE_PETS_NO_CONTROLE = 10;

    public record Grupo(long obrigacoes, long cumpridas, long pets, BigDecimal pcCumprimento) {

        public static Grupo vazio() {
            return new Grupo(0, 0, 0, null);
        }
    }
}
