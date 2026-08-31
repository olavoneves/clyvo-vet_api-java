package br.com.clyvovet.server.config;

import br.com.clyvovet.server.auth.JwtAuthenticationFilter;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.tenant.TenantFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Autenticacao stateless por JWT.
 *
 * <p>Substitui o antigo AuthInterceptor: em vez de atributos soltos no request,
 * a identidade vive no SecurityContext e a autorizacao e declarada por rota.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] ROTAS_PUBLICAS = {
            "/auth/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/**"
    };

    /** Cadastro inicial: quem chama ainda nao tem token para apresentar. */
    private static final String[] CADASTROS_PUBLICOS = {
            "/tutores",
            "/clinicas",
            "/veterinarios"
    };

    /** Prontuario, consulta e anamnese: tutor le pelo /pets/**, mas nao escreve aqui. */
    private static final String[] ROTAS_CLINICAS = {
            "/consultas/**",
            "/anamneses/**",
            "/prescricoes/**",
            "/exames/**",
            "/vacinas/**",
            "/alergias-pet/**",
            "/condicoes-pet/**"
    };

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // instanciados aqui, e nao como @Component, para que o Spring Boot nao os
        // registre tambem na cadeia de filtros do servlet container
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);

        return http
                // API com JWT: nao ha cookie de sessao para um ataque CSRF explorar
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        .requestMatchers(HttpMethod.POST, CADASTROS_PUBLICOS).permitAll()
                        .requestMatchers(ROTAS_CLINICAS).hasAnyRole(
                                TipoUsuario.VETERINARIO.name(), TipoUsuario.COLABORADOR.name())
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> escreverProblema(
                                response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                                "Token de acesso ausente ou invalido"))
                        .accessDeniedHandler((request, response, ex) -> escreverProblema(
                                response, HttpStatus.FORBIDDEN, "Forbidden",
                                "Perfil sem permissao para este recurso")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // depois do JWT: sem SecurityContext preenchido nao ha tenant a propagar
                .addFilterAfter(new TenantFilter(), JwtAuthenticationFilter.class)
                .build();
    }

    /** Mesmo formato ProblemDetail que o GlobalExceptionHandler devolve. */
    private void escreverProblema(HttpServletResponse response, HttpStatus status,
                                  String titulo, String detalhe) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(URI.create("https://clinicavet.com/errors/" + status.name().toLowerCase()));
        problema.setTitle(titulo);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problema);
    }
}
