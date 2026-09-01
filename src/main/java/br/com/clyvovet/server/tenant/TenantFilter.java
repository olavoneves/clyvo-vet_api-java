package br.com.clyvovet.server.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Popula o {@link TenantContext} a partir do usuario ja autenticado.
 *
 * <p>Roda depois de quem autentica — o {@code JwtAuthenticationFilter} na cadeia
 * da API, o filtro de sessao na cadeia das paginas. Sem SecurityContext
 * preenchido nao ha tenant a propagar, e o isolamento vale igual nas duas.
 */
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            AuthenticatedUser.atual()
                    .map(AuthenticatedUser::idClinica)
                    .ifPresent(TenantContext::set);
            filterChain.doFilter(request, response);
        } finally {
            // o container reaproveita threads: sem o clear, a proxima requisicao
            // atendida por esta thread enxergaria o tenant da anterior
            TenantContext.clear();
        }
    }
}
