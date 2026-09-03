package br.com.clyvovet.server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Recurso inexistente, ou inexistente para a clinica que perguntou.
 *
 * <p>O @ResponseStatus e para as telas. Numa chamada de API quem responde e o
 * GlobalExceptionHandler, que devolve ProblemDetail e ganha do @ResponseStatus;
 * numa pagina nao ha handler — o advice e restrito aos @RestController — e sem
 * a anotacao o Spring cairia em 500 para um caso que e 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entity, Long id) {
        super(entity + " with id " + id + " not found");
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
