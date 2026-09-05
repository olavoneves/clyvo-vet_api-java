package br.com.clyvovet.server.agente;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracao do agente de agendamento.
 *
 * <p>Dividida em duas partes: o que vale para qualquer provedor — teto de
 * voltas, timeout, retentativas — e um bloco por provedor, com a chave, o modelo
 * e o endereco de cada um. A divisao segue a porta {@code ProvedorLlm}: o que e
 * do laco fica fora, o que e de fornecedor fica dentro do bloco dele.
 *
 * <p>Nenhuma chave tem valor padrao, e as duas podem faltar. Quando a do
 * provedor ativo falta, o agente fica indisponivel e o resto do sistema segue de
 * pe: nenhuma outra funcionalidade depende deste modulo.
 *
 * <p>Note que a disponibilidade nao e perguntada a este record e sim ao provedor
 * ativo — cada um sabe qual e a sua chave. Ver {@code ProvedorLlm.disponivel()}.
 *
 * @param maxIteracoes voltas de ferramenta por turno antes de escalar
 * @param maxTokens    teto de saida por chamada; e limite, nao meta
 * @param timeout      espera maxima por resposta do provedor
 * @param maxRetentativas chamadas extras apos falha retentavel (429 e 5xx)
 * @param validadeDaConversa quanto tempo o historico sobrevive sem uso
 * @param anthropic    bloco do provedor Anthropic (ANTHROPIC_API_KEY)
 * @param gemini       bloco do provedor Gemini (GEMINI_API_KEY)
 */
@ConfigurationProperties(prefix = "app.agente")
public record AgenteProperties(
        int maxIteracoes,
        int maxTokens,
        Duration timeout,
        int maxRetentativas,
        Duration validadeDaConversa,
        Provedor anthropic,
        Provedor gemini
) {

    public AgenteProperties {
        maxIteracoes = maxIteracoes > 0 ? maxIteracoes : 6;
        maxTokens = maxTokens > 0 ? maxTokens : 8192;
        timeout = timeout != null ? timeout : Duration.ofSeconds(30);
        maxRetentativas = maxRetentativas >= 0 ? maxRetentativas : 2;
        validadeDaConversa = validadeDaConversa != null ? validadeDaConversa : Duration.ofHours(24);

        anthropic = Provedor.comPadroes(anthropic,
                "claude-sonnet-4-6", "https://api.anthropic.com");
        gemini = Provedor.comPadroes(gemini,
                "gemini-2.5-flash", "https://generativelanguage.googleapis.com");
    }

    /**
     * O que e especifico de um provedor.
     *
     * @param chaveApi credencial; vazia desliga este provedor
     * @param modelo   id do modelo, como o provedor o nomeia
     * @param urlBase  host da API; parametrizado para o stub dos testes
     */
    public record Provedor(String chaveApi, String modelo, String urlBase) {

        /** Sem chave, sem provedor. */
        public boolean disponivel() {
            return chaveApi != null && !chaveApi.isBlank();
        }

        /**
         * O bloco com os buracos preenchidos.
         *
         * <p>Aceita o bloco inteiro nulo: um ambiente que nunca ouviu falar do
         * provedor alternativo nao precisa declarar nada sobre ele — ele nasce
         * completo e desligado, por falta de chave.
         */
        static Provedor comPadroes(Provedor provedor, String modelo, String urlBase) {
            if (provedor == null) {
                return new Provedor(null, modelo, urlBase);
            }
            return new Provedor(
                    provedor.chaveApi(),
                    ouEntao(provedor.modelo(), modelo),
                    ouEntao(provedor.urlBase(), urlBase));
        }

        private static String ouEntao(String valor, String padrao) {
            return valor == null || valor.isBlank() ? padrao : valor;
        }
    }
}
