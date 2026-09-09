package br.com.clyvovet.server.config;

import br.com.clyvovet.server.auth.JwtAuthenticationFilter;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.tenant.TenantFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

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
 *
 * <p><b>A cadeia da API termina em {@code denyAll}.</b> Terminava em
 * {@code anyRequest().authenticated()}, que se le como "aberto para qualquer
 * usuario autenticado, menos o que alguem lembrou de fechar" — e o que alguem
 * esquecia de fechar nascia alcancavel por todo perfil. Foi assim que um token
 * de tutor chegou a {@code /api/tutores}, que devolve o cadastro de todos os
 * tutores da clinica. Com {@code denyAll} a omissao passa a errar para o lado
 * seguro: rota nova nasce negada e so abre quando alguem a declara aqui.
 *
 * <p>O preco e conhecido e aceito: controller novo cujo caminho nao entre em
 * nenhuma das listas responde 403 mesmo para quem deveria alcanca-lo. E um 403
 * na primeira chamada, em desenvolvimento, no lugar de um vazamento em
 * producao que ninguem ve.
 */
@Slf4j
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

    /**
     * A vitrine de clinicas que a tela de cadastro do aplicativo le antes de
     * existir um token.
     *
     * <p>{@code POST /tutores} exige {@code clinicaId} e o aplicativo nao tinha
     * como descobri-lo: {@code GET /clinicas} responde 401 para quem ainda nao
     * tem conta. Esta e a unica leitura aberta da API, e devolve uma projecao
     * reduzida — ver {@code ClinicaPublicaResponse}.
     */
    private static final String[] API_LEITURAS_PUBLICAS = {
            "/clinicas/publicas"
    };

    /**
     * A API de gestao da clinica. So {@code VETERINARIO} e {@code COLABORADOR}.
     *
     * <p>{@code /pets/**} e {@code /agendamentos/**} entraram aqui porque estavam
     * caindo no {@code anyRequest().authenticated()}, e isso bastava para um token
     * de tutor alcanca-las. Nenhuma delas verifica dono: o id do tutor vinha da
     * URL em {@code /pets/tutor/{id}} e do corpo no cadastro, entao trocar o
     * numero era ler e escrever no nome de outro tutor da mesma clinica. O
     * isolamento entre clinicas nunca esteve em jogo — o que faltava era o
     * isolamento dentro de uma.
     *
     * <p>O tutor nao fica sem superficie: o aplicativo dele passa a falar com
     * {@code /api/tutor/**}, onde o dono sai do token e nao do parametro.
     */
    private static final String[] API_ROTAS_CLINICAS = {
            "/pets/**",
            "/agendamentos/**",
            "/consultas/**",
            "/anamneses/**",
            "/prescricoes/**",
            "/exames/**",
            "/vacinas/**",
            "/alergias-pet/**",
            "/condicoes-pet/**",
            // motor de protocolo: obrigacao e receita sao gestao da clinica
            "/obrigacoes/**",
            "/painel/**",
            // ciclo de vida dos cadastros. So o POST de clinica e o de tutor
            // sao publicos, e os dois ja passaram na regra acima: ler, alterar
            // e remover cadastro e ato administrativo da equipe.
            "/clinicas/**",
            "/tutores/**",
            "/veterinarios/**",
            // catalogo clinico. Especie, raca e protocolo sao a materia-prima
            // do motor: quem os edita muda o cuidado de todos os pets. O tutor
            // le especie, raca e veterinario em /tutor/catalogo, que e somente
            // leitura: escolher dentro do catalogo nao e o mesmo que edita-lo.
            "/especies/**",
            "/racas/**",
            "/protocolos/**",
            "/medicamentos/**",
            "/tipos-alergia/**",
            "/tipos-condicao/**",
            "/tipos-sensor/**",
            "/tipos-vacina/**",
            // telemetria: sensor, leitura e alerta sao instrumentacao da clinica
            "/sensores-iot/**",
            "/leituras-iot/**",
            "/alertas-iot/**",
            // diagnostico: a trilha de erro do motor conta como o sistema falhou
            "/logs-erro/**"
    };

    /**
     * Rotas do aplicativo do tutor.
     *
     * <p>Duas superficies que so o tutor alcanca. O agente de agendamento, porque
     * veterinario e colaborador tem a agenda inteira nas telas de gestao e nao
     * precisam conversar com um assistente para marcar o que ja podem marcar. E
     * {@code /tutor/**}, o CRUD do aplicativo, onde o dono do pet e derivado do
     * usuario autenticado e nunca aceito como parametro.
     *
     * <p>Nao confundir com {@code /tutores}, que e o cadastro de tutor da API de
     * gestao e continua sendo outra coisa: {@code /api/tutor/**} nao tem como
     * endereçar um tutor que nao seja o do token.
     */
    private static final String[] API_ROTAS_DO_TUTOR = {
            "/agente/**",
            "/tutor/**"
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

    /**
     * Telas do tutor. Servidas pela mesma cadeia de formLogin das telas da
     * clinica, e nao pela API: e uma pagina com sessao em cookie, nao um cliente
     * de token.
     */
    private static final String[] PAGINAS_DO_TUTOR = {
            "/tutor",
            "/tutor/**"
    };

    /** Telas de gestao da clinica. O tutor nao entra aqui. */
    private static final String[] PAGINAS_DA_CLINICA = {
            "/",
            "/painel/**",
            "/pets/**",
            "/agenda/**",
            // o botao de enviar lembrete, servido pela cadeia de sessao como o
            // resto das telas — nao confundir com /api/obrigacoes/**
            "/obrigacoes/**"
    };

    /** HSTS de um ano: abaixo disso os navegadores ignoram o cabecalho. */
    private static final long HSTS_SEGUNDOS = Duration.ofDays(365).toSeconds();

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    /**
     * Origens autorizadas a chamar a API de dentro de um navegador.
     *
     * <p>Lista explicita, nunca {@code *}: com {@code allowCredentials} ligado o
     * curinga e recusado pelo proprio navegador, e sem credenciais ele
     * autorizaria qualquer site a falar com a API em nome de quem estiver logado.
     * Vazio significa nenhuma origem cruzada — que e o certo para uma API
     * consumida so por aplicativo nativo.
     */
    @Value("${app.cors.allowed-origins:}")
    private List<String> origensPermitidas;

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
                .cors(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(SecurityConfig::cabecalhosDeSeguranca)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(api(API_PUBLICA)).permitAll()
                        .requestMatchers(HttpMethod.POST, api(API_CADASTROS_PUBLICOS)).permitAll()
                        // antes da regra de /clinicas/**, senao cai na API de gestao
                        .requestMatchers(HttpMethod.GET, api(API_LEITURAS_PUBLICAS)).permitAll()
                        // contratar veterinario e ato administrativo da clinica
                        .requestMatchers(HttpMethod.POST, api("/veterinarios"))
                                .hasRole(TipoUsuario.COLABORADOR.name())
                        .requestMatchers(api(API_ROTAS_CLINICAS)).hasAnyRole(
                                TipoUsuario.VETERINARIO.name(), TipoUsuario.COLABORADOR.name())
                        .requestMatchers(api(API_ROTAS_DO_TUTOR)).hasRole(TipoUsuario.TUTOR.name())
                        // fechado por padrao: ver a nota da classe
                        .anyRequest().denyAll())
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
     *
     * <p>Cobre tambem o actuator: {@code /actuator/health} aberto para o health
     * check do container, o resto atras de autenticacao.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain paginasFilterChain(HttpSecurity http) throws Exception {
        return http
                .headers(SecurityConfig::cabecalhosDeSeguranca)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers(ESTATICOS).permitAll()
                        .requestMatchers(DOCUMENTACAO).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers(PAGINAS_DO_TUTOR).hasRole(TipoUsuario.TUTOR.name())
                        .requestMatchers(PAGINAS_DA_CLINICA).hasAnyRole(
                                TipoUsuario.VETERINARIO.name(), TipoUsuario.COLABORADOR.name())
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        // destino por perfil, sempre: o tutor nao tem painel de
                        // receita e cairia num 403 logo depois de acertar a senha
                        .successHandler(SecurityConfig::destinoDepoisDoLogin)
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(origensPermitidas);
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuracao.setAllowCredentials(true);
        configuracao.setMaxAge(Duration.ofHours(1));

        if (origensPermitidas.isEmpty()) {
            log.info("CORS sem origens configuradas: nenhuma chamada cruzada sera aceita. "
                    + "Defina app.cors.allowed-origins se um front-end web precisar da API.");
        }

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration(WebMvcConfig.PREFIXO_API + "/**", configuracao);
        return fonte;
    }

    /**
     * Cabecalhos identicos nas duas cadeias.
     *
     * <p>{@code nosniff} impede o navegador de reinterpretar o tipo declarado;
     * {@code DENY} tira a aplicacao de qualquer iframe, o que fecha clickjacking;
     * {@code same-origin} evita vazar a URL interna — que carrega id de pet e de
     * clinica — no Referer de um link externo. HSTS so tem efeito sobre HTTPS,
     * entao nao atrapalha o ambiente local em http.
     */
    private static void cabecalhosDeSeguranca(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(HSTS_SEGUNDOS));
    }

    /**
     * Para onde cada perfil vai depois de entrar.
     *
     * <p>Ignora a pagina que o usuario tentava abrir antes do login, de proposito:
     * quem digitou /login direto nao tem pagina salva, e restaurar uma pagina de
     * outro perfil seria mandar o tutor para uma tela que ele nao pode ver.
     */
    private static void destinoDepoisDoLogin(HttpServletRequest request,
                                             HttpServletResponse response,
                                             Authentication autenticacao) throws IOException {
        String papelDeTutor = "ROLE_" + TipoUsuario.TUTOR.name();
        boolean tutor = autenticacao.getAuthorities().stream()
                .anyMatch(a -> papelDeTutor.equals(a.getAuthority()));

        new DefaultRedirectStrategy().sendRedirect(
                request, response, tutor ? "/tutor" : "/painel/receita");
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
