package br.com.clyvovet.server.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Exercita o filtro direto, sem contexto do Spring.
 *
 * <p>O filtro e registrado no container por FilterRegistrationBean, fora da
 * cadeia do Spring Security — uma fatia @WebMvcTest nao o executaria, e o teste
 * passaria sem nunca ter tocado no codigo sob prova.
 */
class RateLimitFilterTest {

    private static final int TETO_AUTENTICACAO = 5;
    private static final int TETO_PADRAO = 100;

    private RateLimitFilter filtro;
    private FilterChain cadeia;

    @BeforeEach
    void preparar() {
        filtro = new RateLimitFilter(
                new RateLimitProperties(true, TETO_AUTENTICACAO, TETO_PADRAO,
                        List.of("/api/auth/login", "/api/auth/refresh", "/login"),
                        "X-Forwarded-For"),
                new ObjectMapper());
        cadeia = mock(FilterChain.class);
    }

    @Test
    void sextaTentativaDeLoginEmUmMinutoDevolve429() throws Exception {
        for (int tentativa = 1; tentativa <= TETO_AUTENTICACAO; tentativa++) {
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            filtro.doFilter(requisicao("/api/auth/login", "203.0.113.7"), resposta, cadeia);
            assertThat(resposta.getStatus())
                    .as("tentativa %d dentro do teto", tentativa)
                    .isEqualTo(HttpStatus.OK.value());
        }

        MockHttpServletResponse sexta = new MockHttpServletResponse();
        filtro.doFilter(requisicao("/api/auth/login", "203.0.113.7"), sexta, cadeia);

        assertThat(sexta.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(sexta.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(Integer.parseInt(sexta.getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
        assertThat(sexta.getContentAsString()).contains("Too Many Requests");

        // as 5 primeiras seguiram; a sexta parou no filtro
        verify(cadeia, times(TETO_AUTENTICACAO)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    /** O balde e por IP: um cliente estourando o limite nao pode barrar os outros. */
    @Test
    void ipsDiferentesTemBaldesSeparados() throws Exception {
        for (int i = 0; i < TETO_AUTENTICACAO + 1; i++) {
            filtro.doFilter(requisicao("/api/auth/login", "203.0.113.7"),
                    new MockHttpServletResponse(), cadeia);
        }

        MockHttpServletResponse outroCliente = new MockHttpServletResponse();
        filtro.doFilter(requisicao("/api/auth/login", "198.51.100.4"), outroCliente, cadeia);

        assertThat(outroCliente.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    /** Login e trafego comum contam em baldes distintos, com tetos distintos. */
    @Test
    void rotaComumNaoGastaOTetoDeAutenticacao() throws Exception {
        for (int i = 0; i < TETO_AUTENTICACAO + 1; i++) {
            filtro.doFilter(requisicao("/api/pets", "203.0.113.7"),
                    new MockHttpServletResponse(), cadeia);
        }

        MockHttpServletResponse login = new MockHttpServletResponse();
        filtro.doFilter(requisicao("/api/auth/login", "203.0.113.7"), login, cadeia);

        assertThat(login.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    /** Um pico de trafego nao pode derrubar o health check do container. */
    @Test
    void healthCheckNuncaEBarrado() throws Exception {
        RateLimitFilter apertado = new RateLimitFilter(
                new RateLimitProperties(true, 1, 1, List.of(), "X-Forwarded-For"),
                new ObjectMapper());

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            apertado.doFilter(requisicao("/actuator/health", "203.0.113.7"), resposta, cadeia);
            assertThat(resposta.getStatus()).isEqualTo(HttpStatus.OK.value());
        }
    }

    @Test
    void desligadoDeixaTudoPassar() throws Exception {
        RateLimitFilter desligado = new RateLimitFilter(
                new RateLimitProperties(false, 1, 1, List.of("/login"), ""),
                new ObjectMapper());

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            desligado.doFilter(requisicao("/login", "203.0.113.7"), resposta, cadeia);
            assertThat(resposta.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        // nenhuma das 10 parou no filtro
        verify(cadeia, times(10)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private MockHttpServletRequest requisicao(String uri, String ip) {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("POST", uri);
        requisicao.setRequestURI(uri);
        requisicao.addHeader("X-Forwarded-For", ip);
        return requisicao;
    }
}
