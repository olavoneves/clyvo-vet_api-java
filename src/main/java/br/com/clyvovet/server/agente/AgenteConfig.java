package br.com.clyvovet.server.agente;

import br.com.clyvovet.server.agente.llm.AnthropicAdapter;
import br.com.clyvovet.server.agente.llm.GeminiAdapter;
import br.com.clyvovet.server.agente.llm.ProvedorLlm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Liga o agente ao resto da aplicacao e escolhe o provedor de LLM.
 *
 * <p>A escolha e feita aqui, no arranque, e nao dentro do laco: existe
 * exatamente um bean de {@link ProvedorLlm} no contexto, e o
 * {@code AgenteService} recebe o que houver sem nunca perguntar qual e. Uma
 * selecao feita a cada chamada precisaria dos dois clientes vivos e espalharia
 * o nome do fornecedor pelo codigo de dominio.
 *
 * <p>O provedor e criado mesmo sem chave configurada, e isso e proposital. Um
 * bean condicional a chave faria o {@code AgenteService} nao existir, o
 * {@code AgenteController} nao subir e a aplicacao inteira falhar no arranque
 * por dependencia faltando — exatamente o oposto do requisito, que e a aplicacao
 * subir normal e apenas este endpoint responder 503.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AgenteProperties.class)
public class AgenteConfig {

    /** Qual adaptador atende. Ausente significa Gemini, o provedor ativo. */
    public static final String PROPRIEDADE_PROVEDOR = "clyvo.agente.provedor";

    /**
     * Gemini: o provedor ativo, e o padrao quando a propriedade nao esta escrita.
     *
     * <p>Ativo por custo — o tier gratuito atende a demonstracao inteira sem
     * cartao, e este projeto nao pode ter custo.
     */
    @Bean
    @ConditionalOnProperty(name = PROPRIEDADE_PROVEDOR,
            havingValue = GeminiAdapter.NOME, matchIfMissing = true)
    public ProvedorLlm provedorGemini(RestClient.Builder builder, AgenteProperties propriedades) {
        return anunciar(new GeminiAdapter(
                GeminiAdapter.configurar(builder, propriedades), propriedades));
    }

    /** Anthropic: o provedor alternativo, a um valor de propriedade de distancia. */
    @Bean
    @ConditionalOnProperty(name = PROPRIEDADE_PROVEDOR, havingValue = AnthropicAdapter.NOME)
    public ProvedorLlm provedorAnthropic(RestClient.Builder builder,
                                         AgenteProperties propriedades,
                                         ObjectMapper objectMapper) {
        return anunciar(new AnthropicAdapter(
                AnthropicAdapter.configurar(builder, propriedades), propriedades, objectMapper));
    }

    /**
     * Diz no arranque qual provedor atende e se ele tem chave.
     *
     * <p>E a unica pista que o log da sobre um 503 do agente. Sem esta linha, um
     * ambiente com a chave do provedor errado configurada se comporta exatamente
     * como um sem chave nenhuma, e nada explica por que.
     */
    private static ProvedorLlm anunciar(ProvedorLlm provedor) {
        if (provedor.disponivel()) {
            log.info("Agente de agendamento ativo pelo provedor {}", provedor.nome());
        } else {
            log.warn("Provedor {} sem chave de API: o agente de agendamento responderá 503. "
                    + "O restante da aplicação não é afetado.", provedor.nome());
        }
        return provedor;
    }
}
