package br.com.clyvovet.server.agente;

/**
 * A API de mensagens nao respondeu, ou respondeu erro apos as retentativas.
 *
 * <p>Nunca chega ao tutor: o {@link AgenteService} a converte em escalonamento
 * para o veterinario. Uma indisponibilidade nossa nao pode virar stack trace na
 * tela de quem so queria marcar um horario.
 */
public class FalhaNaApiException extends RuntimeException {

    public FalhaNaApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public FalhaNaApiException(String message) {
        super(message);
    }
}
