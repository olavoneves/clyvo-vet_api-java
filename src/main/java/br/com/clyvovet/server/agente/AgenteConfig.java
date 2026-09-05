package br.com.clyvovet.server.agente;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Liga o agente ao resto da aplicacao.
 *
 * <p>O cliente e criado mesmo sem chave configurada, e isso e proposital. Um
 * bean condicional faria o {@code AgenteService} nao existir, o
 * {@code AgenteController} nao subir e a aplicacao inteira falhar no arranque
 * por dependencia faltando — exatamente o oposto do requisito, que e a aplicacao
 * subir normal e apenas este endpoint responder 503.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AgenteProperties.class)
public class AgenteConfig {

    @Bean
    public AnthropicClient anthropicClient(RestClient.Builder builder, AgenteProperties propriedades) {
        if (propriedades.disponivel()) {
            log.info("Agente de agendamento ativo, modelo {}", propriedades.modelo());
        } else {
            log.warn("ANTHROPIC_API_KEY ausente: o agente de agendamento responderá 503. "
                    + "O restante da aplicação não é afetado.");
        }
        return new AnthropicClient(AnthropicClient.configurar(builder, propriedades), propriedades);
    }
}
