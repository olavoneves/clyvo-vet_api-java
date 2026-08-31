package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.enums.TipoUsuario;

/**
 * Principal guardado no SecurityContext. Substitui os atributos soltos que o
 * antigo AuthInterceptor jogava no HttpServletRequest.
 *
 * <p>Todo perfil pertence a uma clinica, entao idClinica nunca e nulo aqui:
 * o {@code TenantFilter} le esse campo para montar o TenantContext.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        String nome,
        TipoUsuario tipo,
        Long idClinica
) {
}
