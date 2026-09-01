package br.com.clyvovet.server.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serve a tela de login.
 *
 * <p>Quem autentica e o filtro do Spring Security no POST /login — este
 * controller so responde pelo GET. Os parametros {@code erro} e {@code saiu}
 * chegam pelos redirecionamentos configurados na SecurityConfig e o template
 * os le direto de {@code param}.
 */
@Controller
public class LoginWebController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
