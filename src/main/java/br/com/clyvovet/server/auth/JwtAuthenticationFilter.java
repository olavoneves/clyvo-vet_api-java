package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.enums.TipoUsuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Valida o Bearer token e popula o SecurityContext.
 *
 * <p>Token ausente ou invalido nao interrompe a cadeia: a requisicao segue
 * anonima e quem decide o 401 e o entry point da SecurityConfig. Assim uma
 * rota publica com token vencido continua funcionando.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(PREFIXO_BEARER)) {
            try {
                Claims claims = jwtService.parseToken(header.substring(PREFIXO_BEARER.length()));
                AuthenticatedUser usuario = new AuthenticatedUser(
                        claims.get("id", Long.class),
                        claims.getSubject(),
                        claims.get("nome", String.class),
                        TipoUsuario.valueOf(claims.get("tipo", String.class)),
                        claims.get(JwtService.CLAIM_ID_CLINICA, Long.class));

                var authentication = new UsernamePasswordAuthenticationToken(
                        usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.tipo().name())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                log.warn("Token rejeitado: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
