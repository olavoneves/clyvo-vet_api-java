package br.com.clyvovet.server.config;

import br.com.clyvovet.server.auth.JwtAuthenticationFilter;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.tenant.TenantFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Duas cadeias que nao se misturam.
 *
 * <p>A API vive sob {@code /api} e e stateless: identidade no Bearer token, sem
 * cookie, erro em ProblemDetail. As paginas vivem na raiz e sao stateful: login
 * por formulario, sessao em cookie, erro por redirecionamento. Sao modelos de
 * autenticacao incompativeis — uma cadeia unica tentando servir os dois acabaria
 * criando sessao para o mobile e devolvendo JSON para o browser.
 *
 * <p>O prefixo {@code /api} nao esta escrito nos controllers: quem o aplica e o
 * {@link WebMvcConfig}. As constantes daqui declaram a rota como o controller a
 * conhece e passam por {@link #api}, para que as duas coisas nao possam
 * divergir em silencio.
 *
 * <p>Nas duas cadeias o {@code TenantFilter} entra logo depois de quem
 * autentica: o isolamento por clinica vale igual na tela e na API.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Login, refresh e logout: quem chama ainda nao tem token para apresentar. */
    private static final String[] API_PUBLICA = {
            "/auth/**"
    };

    /**
     * Os dois unicos cadastros abertos. A clinica se cadastra na plataforma e o
     * tutor se cadastra na clinica: nenhum dos dois tem token para apresentar.
     * Veterinario nao entra aqui — quem contrata veterinario e a clinica, que
     * a essa altura ja esta autenticada.
     */
    private static final String[] API_CADASTROS_PUBLICOS = {
            "/clinicas",
            "/tutores"
    };

    /** Prontuario, consulta e anamnese: tutor le pelo /pets/**, mas nao escreve aqui. */
    private static final String[] API_ROTAS_CLINICAS = {
            "/consultas/**",
            "/anamneses/**",
            "/prescricoes/**",
            "/exames/**",
            "/vacinas/**",
            "/alergias-pet/**",
            "/condicoes-pet/**",
            // motor de protocolo: obrigacao e receita sao gestao da clinica
            "/obrigacoes/**",
            "/painel/**"
    };

    /** Documentacao da API. Fora de /api: quem a serve e o springdoc. */
    private static final String[] DOCUMENTACAO = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    /** Servidos do classpath, sem identidade envolvida. */
    private static final String[] ESTATICOS = {
            "/css/**",
            "/js/**",
            "/images/**",
            "/favicon.ico"
    };

    /** Telas de gestao da clinica. O tutor usa o app, nao estas paginas. */
    private static final String[] PAGINAS_DA_CLINICA = {
            "/",
            "/painel/**",
            "/pets/**",
            "/agenda/**"
    };

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    /**
     * Cadeia da API. Vem primeiro: o {@code securityMatcher} restringe seu
     * alcance a {@code /api/**} e tudo que nao casar cai na cadeia seguinte.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        // instanciados aqui, e nao como @Component, para que o Spring Boot nao os
        // registre tambem na cadeia de filtros do servlet container
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);

        return http
                .securityMatcher(WebMvcConfig.PREFIXO_API + "/**")
                // API com JWT: nao ha cookie de sessao para um ataque CSRF explorar
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(api(API_PUBLICA)).permitAll()
                        .requestMatchers(HttpMethod.POST, api(API_CADASTROS_PUBLICOS)).permitAll()
                        // contratar veterinario e ato administrativo da clinica
                        .requestMatchers(HttpMethod.POST, api("/veterinarios"))
                                .hasRole(TipoUsuario.COLABORADOR.name())
                        .requestMatchers(api(API_ROTAS_CLINICAS)).hasAnyRole(
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

    /**
     * Cadeia das paginas. Sessao de verdade: o cookie e a credencial, entao o
     * CSRF fica ligado — o Thymeleaf injeta o token em todo formulario com
     * {@code th:action}.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain paginasFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers(ESTATICOS).permitAll()
                        .requestMatchers(DOCUMENTACAO).permitAll()
                        .requestMatchers(PAGINAS_DA_CLINICA).hasAnyRole(
                                TipoUsuario.VETERINARIO.name(), TipoUsuario.COLABORADOR.name())
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        // true: quem digitou /login direto tambem cai no painel, em
                        // vez de numa pagina salva que nunca existiu
                        .defaultSuccessUrl("/painel/receita", true)
                        .failureUrl("/login?erro")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?saiu")
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                // o SecurityContext ja veio da sessao neste ponto da cadeia
                .addFilterAfter(new TenantFilter(), SecurityContextHolderFilter.class)
                .build();
    }

    /** Rota como o controller a declara -> rota como o servidor a expoe. */
    private static String api(String rota) {
        return WebMvcConfig.PREFIXO_API + rota;
    }

    private static String[] api(String... rotas) {
        return Arrays.stream(rotas).map(SecurityConfig::api).toArray(String[]::new);
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
