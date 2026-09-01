package br.com.clyvovet.server.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduz excecao em ProblemDetail para a API.
 *
 * <p>Restrito aos {@code @RestController}: os controllers de pagina precisam
 * cair no tratamento de erro do Spring Boot e renderizar {@code error.html}.
 * Sem esse recorte, um erro numa tela devolveria JSON no lugar da pagina.
 *
 * <p>Nenhum handler devolve stack trace no corpo. O rastro fica no log, onde
 * so quem opera o sistema alcanca — mensagem de erro detalhada e um mapa da
 * aplicacao para quem esta sondando.
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
        log.error("Entity not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://clinicavet.com/errors/not-found"));
        pd.setTitle("Resource Not Found");
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Data integrity violation: duplicate or referenced record");
        pd.setType(URI.create("https://clinicavet.com/errors/conflict"));
        pd.setTitle("Data Conflict");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        log.error("Validation error on {}: {}", ex.getObjectName(), ex.getMessage());
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed");
        pd.setType(URI.create("https://clinicavet.com/errors/validation"));
        pd.setTitle("Validation Error");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Illegal argument: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://clinicavet.com/errors/business"));
        pd.setTitle("Business Rule Violation");
        return pd;
    }

    @ExceptionHandler(ConflitoDeEstadoException.class)
    public ProblemDetail handleConflitoDeEstado(ConflitoDeEstadoException ex) {
        log.warn("Conflito de estado: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://clinicavet.com/errors/conflito-de-estado"));
        pd.setTitle("State Conflict");
        return pd;
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create("https://clinicavet.com/errors/unauthorized"));
        pd.setTitle("Unauthorized");
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acesso negado: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Perfil sem permissao para este recurso");
        pd.setType(URI.create("https://clinicavet.com/errors/forbidden"));
        pd.setTitle("Forbidden");
        return pd;
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ProblemDetail handleExpiredJwt(ExpiredJwtException ex) {
        log.warn("Expired JWT: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Token expirado");
        pd.setType(URI.create("https://clinicavet.com/errors/unauthorized"));
        pd.setTitle("Token Expirado");
        return pd;
    }

    @ExceptionHandler(JwtException.class)
    public ProblemDetail handleJwt(JwtException ex) {
        log.warn("Invalid JWT: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Token inválido");
        pd.setType(URI.create("https://clinicavet.com/errors/unauthorized"));
        pd.setTitle("Token Inválido");
        return pd;
    }

    /**
     * Rede de seguranca: qualquer excecao nao prevista vira 500 generico.
     *
     * <p>O log leva a excecao inteira porque e o unico lugar onde ela ainda serve
     * para alguma coisa; a resposta leva so a mensagem fixa. Sem este handler o
     * Spring devolveria a mensagem da excecao original, que costuma carregar
     * nome de tabela, SQL e caminho de classe.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleInesperada(Exception ex) {
        log.error("Erro nao tratado", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno ao processar a requisição");
        pd.setType(URI.create("https://clinicavet.com/errors/internal"));
        pd.setTitle("Internal Server Error");
        return pd;
    }
}
