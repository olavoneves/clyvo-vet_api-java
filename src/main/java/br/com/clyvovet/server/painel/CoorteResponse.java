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
     * Piso principal: obrigacoes resolvidas no grupo de controle.
     *
     * <p><b>E a quantidade de obrigacoes que sustenta a comparacao, e nao a de
     * animais.</b> O piso era de dez pets, e media a coisa errada: 178 obrigacoes
     * resolvidas sao amostra confortavel mesmo vindo de 11 pets, e o card as
     * escondia por um criterio que nao falava sobre elas. Pior, o criterio era
     * fragil de um jeito invisivel — o sorteio e deterministico por pet, mas os
     * pets mudam a cada seed, entao uma base nova podia cair em 8 e o numero
     * principal do painel simplesmente sumia da tela sem nada ter piorado.
     *
     * <p>Cinquenta porque e onde um desfecho individual para de mover a taxa em
     * ponto percentual inteiro: com 50 resolvidas, um caso vale 2 p.p.; com 10,
     * vale 10 p.p., que e a ordem de grandeza do proprio efeito que se quer medir.
     */
    public static final int MINIMO_DE_OBRIGACOES_NO_CONTROLE = 50;

    /**
     * Piso secundario, em pets, contra a amostra concentrada.
     *
     * <p>Cinquenta obrigacoes de dois pets nao sao cinquenta observacoes
     * independentes: as obrigacoes de um mesmo animal sobem e descem juntas com o
     * comportamento de um unico tutor. O piso de pets continua existindo por isso,
     * mas <b>mais baixo</b> — ele deixou de ser o criterio e passou a ser a guarda
     * contra o caso degenerado que ja aconteceu de verdade, com um pet so no
     * controle dizendo que o produto derrubava o comparecimento em vinte pontos.
     */
    public static final int MINIMO_DE_PETS_NO_CONTROLE = 5;

    /**
     * Os dois pisos, para a tela poder cita-los sem repetir os valores.
     *
     * <p>A ressalva de amostra insuficiente nomeia os numeros. Escreve-los no HTML
     * criaria duas fontes da mesma verdade, e a do HTML nao acompanharia uma
     * recalibragem do piso — a tela seguiria plausivel dizendo o valor antigo, que
     * e o pior tipo de erro.
     */
    public int minimoDeObrigacoesNoControle() {
        return MINIMO_DE_OBRIGACOES_NO_CONTROLE;
    }

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
     *
     * <p><b>O universo sao os pets com obrigacao ja resolvida</b>, e nao todos os
     * pets da clinica — o denominador e o mesmo do card, que so conta CUMPRIDA e
     * PERDIDA. Os dois numeros sao proximos mas nao iguais (na base local, 245
     * contra 250), e a tela dizia "dos pets desta clinica", o que prometia o
     * universo maior. O texto agora nomeia o universo que de fato usa: um numero
     * certo com rotulo errado continua sendo um numero errado.
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

    /**
     * Pets nos dois grupos: o denominador de {@link #pcPetsNoControle()}.
     *
     * <p>Chamava-se {@code petsSorteados} e o nome mentia: sorteados sao todos os
     * pets da clinica, inclusive os que ainda nao tem obrigacao resolvida e por
     * isso nao entram na coorte. Estes sao os que a coorte conta.
     */
    public long petsNaCoorte() {
        return tratado.pets() + controle.pets();
    }

    public record Grupo(long obrigacoes, long cumpridas, long pets, BigDecimal pcCumprimento) {

        public static Grupo vazio() {
            return new Grupo(0, 0, 0, null);
        }
    }
}
