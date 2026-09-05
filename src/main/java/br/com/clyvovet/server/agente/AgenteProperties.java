package br.com.clyvovet.server.agente;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracao do agente de agendamento.
 *
 * <p>A chave da API e a unica propriedade sem valor padrao, e a unica que pode
 * faltar em producao. Quando ela falta o agente fica indisponivel e o resto do
 * sistema segue de pe: nenhuma outra funcionalidade depende deste modulo.
 *
 * @param chaveApi     ANTHROPIC_API_KEY. Vazia desliga o agente.
 * @param modelo       modelo da API de mensagens
 * @param urlBase      host da API; parametrizado para o stub dos testes
 * @param maxIteracoes voltas de ferramenta por turno antes de escalar
 * @param maxTokens    teto de saida por chamada; e limite, nao meta
 * @param timeout      espera maxima por resposta da API
 * @param maxRetentativas chamadas extras apos falha retentavel (429 e 5xx)
 * @param validadeDaConversa quanto tempo o historico sobrevive sem uso
 */
@ConfigurationProperties(prefix = "app.agente")
public record AgenteProperties(
        String chaveApi,
        String modelo,
        String urlBase,
        int maxIteracoes,
        int maxTokens,
        Duration timeout,
        int maxRetentativas,
        Duration validadeDaConversa
) {

    public AgenteProperties {
        modelo = ouEntao(modelo, "claude-sonnet-4-6");
        urlBase = ouEntao(urlBase, "https://api.anthropic.com");
        maxIteracoes = maxIteracoes > 0 ? maxIteracoes : 6;
        maxTokens = maxTokens > 0 ? maxTokens : 8192;
        timeout = timeout != null ? timeout : Duration.ofSeconds(30);
        maxRetentativas = maxRetentativas >= 0 ? maxRetentativas : 2;
        validadeDaConversa = validadeDaConversa != null ? validadeDaConversa : Duration.ofHours(24);
    }

    /**
     * Sem chave, sem agente. Verificado no controller para devolver 503 antes de
     * qualquer trabalho, e no cliente como rede de seguranca.
     */
    public boolean disponivel() {
        return chaveApi != null && !chaveApi.isBlank();
    }

    private static String ouEntao(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
