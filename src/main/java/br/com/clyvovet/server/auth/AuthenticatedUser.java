package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.enums.TipoUsuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

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

    /**
     * Usuario da requisicao em curso, quando ha uma autenticada.
     *
     * <p>Aceita as duas portas de entrada: o JWT da API guarda este record como
     * principal, e o login por formulario guarda um {@link ClinicaUserDetails}
     * que o embrulha. Quem consome a identidade — tenant, auditoria — nao
     * precisa saber a diferenca.
     */
    public static Optional<AuthenticatedUser> atual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        return switch (authentication.getPrincipal()) {
            case AuthenticatedUser usuario -> Optional.of(usuario);
            case ClinicaUserDetails detalhes -> Optional.of(detalhes.usuario());
            case null, default -> Optional.empty();
        };
    }

    /** Como o usuario aparece na trilha de auditoria do motor. */
    public static String identificacaoAtual() {
        return atual().map(AuthenticatedUser::email).orElse("sistema");
    }
}
