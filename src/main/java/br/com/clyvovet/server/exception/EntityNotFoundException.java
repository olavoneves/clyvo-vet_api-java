package br.com.clyvovet.server.exception;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entity, Long id) {
        super(entity + " with id " + id + " not found");
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
