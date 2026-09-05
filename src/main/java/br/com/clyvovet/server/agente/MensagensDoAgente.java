package br.com.clyvovet.server.agente;

/**
 * As frases que a aplicacao diz ao tutor quando nao e o modelo quem fala.
 *
 * <p>Ficam juntas e fixas de proposito. Sao exatamente os momentos em que algo
 * saiu do previsto — pergunta clinica, API fora, laco sem fim — e sao os
 * momentos em que o texto mais importa: e a ultima coisa que o tutor le antes de
 * decidir se confia no sistema.
 *
 * <p>Nenhuma delas menciona erro tecnico, modelo ou API. Do lado de la ha uma
 * pessoa preocupada com um animal, nao alguem interessado no nosso incidente.
 */
public final class MensagensDoAgente {

    private MensagensDoAgente() {
    }

    /** Pergunta clinica, por instrucao do modelo ou por barragem na saida. */
    public static final String ESCALONAMENTO = """
            Essa é uma questão de saúde do seu pet, e quem responde isso é o \
            veterinário — eu cuido só de agenda. Já encaminhei sua mensagem para \
            a equipe clínica, e um veterinário vai te responder.

            Se quiser, enquanto isso eu marco uma consulta para ele ser avaliado.""";

    /** A API de mensagens nao respondeu. */
    public static final String INDISPONIVEL = """
            Não consegui te atender agora — o assistente está temporariamente fora do ar. \
            Encaminhei sua mensagem para a equipe da clínica, que entra em contato com você. \
            Se preferir, tente de novo daqui a alguns minutos.""";

    /** O laco esgotou as voltas sem chegar a uma resposta. */
    public static final String SEM_CONCLUSAO = """
            Não consegui concluir esse pedido sozinho. Encaminhei a conversa para a \
            equipe da clínica, e alguém vai te responder para resolver isso com você.""";
}
