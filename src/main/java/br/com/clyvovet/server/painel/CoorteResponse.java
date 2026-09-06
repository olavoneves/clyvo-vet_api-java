package br.com.clyvovet.server.painel;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    /**
     * O piso, para a tela poder citar o numero sem repetir o valor.
     *
     * <p>A ressalva de amostra insuficiente diz "abaixo de N um unico tutor move
     * a taxa". Antes o N estava escrito no HTML, ao lado da constante que o
     * {@code PainelCoorteService} de fato consulta — duas fontes da mesma verdade,
     * e a do HTML nao acompanharia uma mudanca do piso.
     */
    public int minimoDePetsNoControle() {
        return MINIMO_DE_PETS_NO_CONTROLE;
    }

    /**
     * Que fatia dos pets desta clinica caiu no controle, de fato.
     *
     * <p>O sorteio de {@code FN_CLV_GRUPO_CONTROLE} <i>mira</i> 10%, e a tela
     * afirmava esse 10% em texto fixo. Duas coisas erradas nisso: o percentual
     * alvo mora no banco, no parametro da funcao, e o HTML era uma segunda copia
     * dele; e o valor exibido nem era o que a clinica tem — o sorteio e por hash
     * de pet, entao a fatia real de uma clinica com algumas centenas de pets
     * oscila vários pontos em torno do alvo.
     *
     * <p>Devolvendo a proporcao <b>medida</b>, o numero da tela passa a sair da
     * mesma consulta que ja alimenta o card e acompanha a base sozinho. Nulo
     * quando nao ha pet nenhum: dizer "0%" sugeriria que o sorteio nao funcionou.
     */
    public BigDecimal pcPetsNoControle() {
        long total = tratado.pets() + controle.pets();
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(controle.pets())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    /** Pets nos dois grupos: o denominador de {@link #pcPetsNoControle()}. */
    public long petsSorteados() {
        return tratado.pets() + controle.pets();
    }

    public record Grupo(long obrigacoes, long cumpridas, long pets, BigDecimal pcCumprimento) {

        public static Grupo vazio() {
            return new Grupo(0, 0, 0, null);
        }
    }
}
