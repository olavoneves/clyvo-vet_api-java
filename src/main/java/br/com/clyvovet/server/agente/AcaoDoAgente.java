package br.com.clyvovet.server.agente;

/**
 * Algo que o agente mudou no sistema durante o turno.
 *
 * <p>Vai no corpo da resposta para que a tela do tutor saiba quando recarregar
 * as pendencias sem adivinhar pelo texto. Ler intencao do texto do modelo para
 * decidir se a lista mudou seria construir um segundo interpretador, pior que o
 * primeiro.
 *
 * @param tipo      AGENDAMENTO_CRIADO, AGENDAMENTO_REMARCADO ou ESCALONAMENTO
 * @param id        identificador do que foi criado ou alterado, quando ha um
 * @param descricao frase pronta para exibicao
 */
public record AcaoDoAgente(String tipo, Long id, String descricao) {

    public static final String AGENDAMENTO_CRIADO = "AGENDAMENTO_CRIADO";
    public static final String AGENDAMENTO_REMARCADO = "AGENDAMENTO_REMARCADO";
    public static final String ESCALONAMENTO = "ESCALONAMENTO";
}
