package br.com.clyvovet.server.agente;

/**
 * De quem e sobre qual pet e a conversa em curso.
 *
 * <p>Montado pelo {@link AgenteService} a partir do usuario autenticado e do pet
 * que ele provou ser dele — nunca a partir do que o modelo escreveu. Toda
 * ferramenta recebe este contexto como segundo argumento, e o que o modelo
 * mandou como parametro so serve para ser conferido contra ele.
 *
 * <p>E aqui que mora a resposta a "e se o modelo pedir a obrigacao de outro
 * pet?": ele pode pedir; a ferramenta compara com o contexto e recusa.
 */
public record ContextoDoAgente(
        Long idClinica,
        Long idTutor,
        Long idPet,
        String emailDoTutor
) {
}
