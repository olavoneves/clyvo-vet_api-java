package br.com.clyvovet.server.config;

import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    // POST público — cadastro sem autenticação prévia
    private static final Set<String> PUBLIC_POST_PATHS = Set.of(
            "/tutores",
            "/clinicas",
            "/veterinarios"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && PUBLIC_POST_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Token de acesso não fornecido");
        }

        Claims claims = jwtService.parseToken(authHeader.substring(7));

        request.setAttribute("userId", claims.get("id", Long.class));
        request.setAttribute("userTipo", claims.get("tipo", String.class));
        request.setAttribute("userEmail", claims.getSubject());
        request.setAttribute("userNome", claims.get("nome", String.class));

        return true;
    }
}
