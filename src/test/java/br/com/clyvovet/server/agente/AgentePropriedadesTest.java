package br.com.clyvovet.server.agente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As propriedades do arquivo tem que chegar no record.
 *
 * <p>Este teste existe por causa de uma falha que nao aparece em nenhum outro:
 * se uma chave de {@code application.properties} nao casar com o componente do
 * record, o binding nao reclama — ele apenas deixa o valor no padrao. O agente
 * subiria apontando para o modelo errado, ou pior, com {@code chave-api} vazia
 * mesmo havendo chave no ambiente, respondendo 503 em producao sem que nada no
 * log dissesse por que.
 *
 * <p>Sobe so o {@link AgenteConfig}, lendo o {@code application.properties} de
 * verdade — nao um punhado de valores repetidos no teste, que passariam a
 * concordar consigo mesmos e com mais nada.
 */
class AgentePropriedadesTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(AgenteConfig.class);

    @Test
    @DisplayName("o application.properties alimenta o record de configuracao")
    void propriedadesDoArquivoChegamNoRecord() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();

            AgenteProperties propriedades = ctx.getBean(AgenteProperties.class);
            assertThat(propriedades.modelo()).isEqualTo("claude-sonnet-4-6");
            assertThat(propriedades.urlBase()).isEqualTo("https://api.anthropic.com");
            assertThat(propriedades.maxIteracoes()).isEqualTo(6);
            assertThat(propriedades.maxTokens()).isEqualTo(8192);
            assertThat(propriedades.timeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(propriedades.maxRetentativas()).isEqualTo(2);
            assertThat(propriedades.validadeDaConversa()).isEqualTo(Duration.ofHours(24));
        });
    }

    /**
     * O requisito que sustenta todo o resto: o agente e opcional.
     *
     * <p>Sem ANTHROPIC_API_KEY o contexto sobe inteiro e o cliente existe — ele
     * apenas recusa quando chamado. Um bean condicional aqui derrubaria a
     * aplicacao no arranque por dependencia faltando, que e o oposto do pedido.
     */
    @Test
    @DisplayName("sem chave no ambiente o contexto sobe e o agente nasce desligado")
    void semChaveOContextoSobeIgual() {
        contexto.withPropertyValues("app.agente.chave-api=").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(AnthropicClient.class);
            assertThat(ctx.getBean(AgenteProperties.class).disponivel()).isFalse();
        });
    }

    @Test
    @DisplayName("com chave no ambiente o agente nasce ativo")
    void comChaveOAgenteFicaDisponivel() {
        contexto.withPropertyValues("app.agente.chave-api=sk-de-teste").run(ctx ->
                assertThat(ctx.getBean(AgenteProperties.class).disponivel()).isTrue());
    }
}
