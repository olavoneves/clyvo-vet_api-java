package br.com.clyvovet.server.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Popula o {@link TenantContext} a partir do usuario ja autenticado.
 * Roda depois do {@code JwtAuthenticationFilter} — sem SecurityContext nao ha tenant.
 */
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser usuario) {
                TenantContext.set(usuario.idClinica());
            }
            filterChain.doFilter(request, response);
        } finally {
            // o container reaproveita threads: sem o clear, a proxima requisicao
            // atendida por esta thread enxergaria o tenant da anterior
            TenantContext.clear();
        }
    }
}
