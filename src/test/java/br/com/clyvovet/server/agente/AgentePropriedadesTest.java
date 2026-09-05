package br.com.clyvovet.server.agente;

import br.com.clyvovet.server.agente.llm.AnthropicAdapter;
import br.com.clyvovet.server.agente.llm.GeminiAdapter;
import br.com.clyvovet.server.agente.llm.ProvedorLlm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As propriedades do arquivo tem que chegar no record, e a propriedade de
 * provedor tem que escolher o adaptador certo.
 *
 * <p>Este teste existe por causa de uma falha que nao aparece em nenhum outro:
 * se uma chave de {@code application.properties} nao casar com o componente do
 * record, o binding nao reclama — ele apenas deixa o valor no padrao. O agente
 * subiria apontando para o modelo errado, ou pior, com chave vazia mesmo
 * havendo chave no ambiente, respondendo 503 em producao sem que nada no log
 * dissesse por que. Com dois blocos de provedor o risco dobra: um erro de
 * aninhamento faria a chave do Gemini nao chegar em lugar nenhum.
 *
 * <p>Sobe so o {@link AgenteConfig}, lendo o {@code application.properties} de
 * verdade — nao um punhado de valores repetidos no teste, que passariam a
 * concordar consigo mesmos e com mais nada.
 */
class AgentePropriedadesTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withUserConfiguration(AgenteConfig.class);

    @Test
    @DisplayName("o application.properties alimenta os dois blocos de provedor")
    void propriedadesDoArquivoChegamNoRecord() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();

            AgenteProperties propriedades = ctx.getBean(AgenteProperties.class);

            assertThat(propriedades.gemini().modelo()).isEqualTo("gemini-2.5-flash");
            assertThat(propriedades.gemini().urlBase())
                    .isEqualTo("https://generativelanguage.googleapis.com");

            assertThat(propriedades.anthropic().modelo()).isEqualTo("claude-sonnet-4-6");
            assertThat(propriedades.anthropic().urlBase()).isEqualTo("https://api.anthropic.com");

            assertThat(propriedades.maxIteracoes()).isEqualTo(6);
            assertThat(propriedades.maxTokens()).isEqualTo(8192);
            assertThat(propriedades.timeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(propriedades.maxRetentativas()).isEqualTo(2);
            assertThat(propriedades.validadeDaConversa()).isEqualTo(Duration.ofHours(24));
        });
    }

    // ---------- selecao do provedor ----------

    @Test
    @DisplayName("o provedor do application.properties e o Gemini, e ele e o unico bean")
    void oProvedorAtivoEOGemini() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(ProvedorLlm.class);
            assertThat(ctx.getBean(ProvedorLlm.class))
                    .isInstanceOf(GeminiAdapter.class);
            assertThat(ctx.getBean(ProvedorLlm.class).nome()).isEqualTo("gemini");
        });
    }

    /**
     * A promessa que a porta faz: trocar de fornecedor e uma linha de
     * configuracao. Se este teste precisar de qualquer outra mudanca para passar,
     * a porta vazou.
     */
    @Test
    @DisplayName("trocar clyvo.agente.provedor troca o adaptador, e so isso")
    void aPropriedadeTrocaOAdaptador() {
        contexto.withPropertyValues("clyvo.agente.provedor=anthropic").run(ctx -> {
            assertThat(ctx).hasSingleBean(ProvedorLlm.class);
            assertThat(ctx.getBean(ProvedorLlm.class))
                    .isInstanceOf(AnthropicAdapter.class);
            assertThat(ctx.getBean(ProvedorLlm.class).nome()).isEqualTo("anthropic");
        });
    }

    // ---------- o agente e opcional ----------

    /**
     * O requisito que sustenta todo o resto: o agente e opcional.
     *
     * <p>Sem GEMINI_API_KEY o contexto sobe inteiro e o provedor existe — ele
     * apenas recusa quando chamado. Um bean condicional a chave derrubaria a
     * aplicacao no arranque por dependencia faltando, que e o oposto do pedido.
     */
    @Test
    @DisplayName("sem chave no ambiente o contexto sobe e o agente nasce desligado")
    void semChaveOContextoSobeIgual() {
        contexto.withPropertyValues("app.agente.gemini.chave-api=").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(ProvedorLlm.class);
            assertThat(ctx.getBean(ProvedorLlm.class).disponivel()).isFalse();
        });
    }

    @Test
    @DisplayName("com chave no ambiente o agente nasce ativo")
    void comChaveOAgenteFicaDisponivel() {
        contexto.withPropertyValues("app.agente.gemini.chave-api=AIza-de-teste").run(ctx ->
                assertThat(ctx.getBean(ProvedorLlm.class).disponivel()).isTrue());
    }

    /**
     * Cada provedor olha a sua propria chave.
     *
     * <p>O caso que o log de arranque existe para explicar: a chave configurada e
     * a do provedor que <i>nao</i> esta ativo. O agente responde 503 mesmo
     * havendo uma chave no ambiente.
     */
    @Test
    @DisplayName("a chave do provedor inativo nao liga o ativo")
    void chaveDoProvedorErradoNaoConta() {
        contexto.withPropertyValues(
                        "app.agente.gemini.chave-api=",
                        "app.agente.anthropic.chave-api=sk-de-teste")
                .run(ctx -> assertThat(ctx.getBean(ProvedorLlm.class).disponivel()).isFalse());
    }
}
