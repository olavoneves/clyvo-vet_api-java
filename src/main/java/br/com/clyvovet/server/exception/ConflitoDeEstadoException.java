package br.com.clyvovet.server.exception;

/**
 * Regra de estado recusada pelo motor no banco. Nasce das excecoes de aplicacao
 * do PL/SQL (faixa ORA-20000 a ORA-20999), como a transicao invalida de
 * obrigacao. E 409: o pedido esta bem formado, mas conflita com o estado atual.
 */
public class ConflitoDeEstadoException extends RuntimeException {

    public ConflitoDeEstadoException(String message) {
        super(message);
    }
}
