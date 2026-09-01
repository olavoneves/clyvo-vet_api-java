package br.com.clyvovet.server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Credencial ausente, invalida ou expirada.
 *
 * <p>O @ResponseStatus e para as telas. Numa chamada de API quem responde e o
 * GlobalExceptionHandler, que devolve ProblemDetail e ganha do @ResponseStatus;
 * numa pagina nao ha handler — o advice e restrito aos @RestController — e sem
 * a anotacao o Spring cairia em 500 para um caso que e 401.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
