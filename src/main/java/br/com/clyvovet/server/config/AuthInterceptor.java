package br.com.clyvovet.server.config;

import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
