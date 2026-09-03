package br.com.clyvovet.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Separa a API das paginas por prefixo de rota.
 *
 * <p>As telas Thymeleaf precisam de URLs limpas — {@code /pets}, {@code /agenda},
 * {@code /painel/receita} — e sao exatamente as que os {@code @RestController}
 * ja ocupavam. Em vez de reescrever o {@code @RequestMapping} de 27 controllers,
 * o prefixo e aplicado no nivel do handler mapping: todo {@code @RestController}
 * deste projeto passa a responder sob {@code /api}, e os {@code @Controller} de
 * pagina ficam na raiz.
 *
 * <p>O predicado exige o pacote da aplicacao de proposito: os controllers do
 * springdoc tambem sao {@code @RestController}, e prefixar {@code /v3/api-docs}
 * quebraria o Swagger.
 *
 * <p>Consequencia para o mobile: a base URL passa de {@code /} para {@code /api}.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String PREFIXO_API = "/api";

    private static final String PACOTE_APLICACAO = "br.com.clyvovet.server";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(PREFIXO_API, tipo ->
                tipo.getPackageName().startsWith(PACOTE_APLICACAO)
                        && tipo.isAnnotationPresent(RestController.class));
    }
}
