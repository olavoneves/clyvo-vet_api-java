package br.com.clyvovet.server.ratelimit;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Registra o filtro de limite no container, antes da cadeia do Spring Security.
 *
 * <p>A ordem importa: a cadeia de seguranca e registrada em
 * {@link SecurityFilterProperties#DEFAULT_FILTER_ORDER}, e um filtro registrado
 * depois dela so veria requisicoes que ja passaram pela autenticacao — tarde
 * demais para conter tentativa de senha.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private static final int ANTES_DA_SEGURANCA = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10;

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties propriedades, ObjectMapper objectMapper) {

        FilterRegistrationBean<RateLimitFilter> registro = new FilterRegistrationBean<>();
        registro.setFilter(new RateLimitFilter(propriedades, objectMapper));
        registro.addUrlPatterns("/*");
        registro.setOrder(ANTES_DA_SEGURANCA);
        registro.setName("rateLimitFilter");
        return registro;
    }
}
