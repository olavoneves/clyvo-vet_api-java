package br.com.clyvovet.server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Regra de estado recusada pelo motor no banco. Nasce das excecoes de aplicacao
 * do PL/SQL (faixa ORA-20000 a ORA-20999), como a transicao invalida de
 * obrigacao. E 409: o pedido esta bem formado, mas conflita com o estado atual.
 *
 * <p>O @ResponseStatus e para as telas. Numa chamada de API quem responde e o
 * GlobalExceptionHandler, que devolve ProblemDetail e ganha do @ResponseStatus;
 * numa pagina nao ha handler — o advice e restrito aos @RestController — e sem
 * a anotacao o Spring cairia em 500 para um caso que e 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflitoDeEstadoException extends RuntimeException {

    public ConflitoDeEstadoException(String message) {
        super(message);
    }
}
