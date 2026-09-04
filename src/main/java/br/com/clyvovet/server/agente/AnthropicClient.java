package br.com.clyvovet.server.agente;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * Fala com a API de mensagens da Anthropic. Unico ponto da aplicacao que sai
 * para a internet.
 *
 * <p>RestClient direto, sem SDK: o que o agente precisa e uma chamada HTTP com
 * ferramentas declaradas e um laco de execucao proprio, e o laco esta em
 * {@link AgenteService}. Uma dependencia a mais para montar o mesmo JSON so
 * acrescentaria superficie de versao ao build.
 *
 * <p>Duas politicas de falha, deliberadamente diferentes:
 *
 * <ul>
 *   <li><b>429 e 5xx</b> sao transitorios — a mesma requisicao pode dar certo
 *       daqui a pouco. Retentativa com espera crescente.
 *   <li><b>Timeout</b> nao e retentado. Trinta segundos ja e o limite do que o
 *       tutor aceita esperar; uma segunda tentativa transformaria uma espera
 *       ruim em uma inaceitavel. Escala direto.
 * </ul>
 *
 * <p>4xx que nao seja 429 tambem nao e retentado: requisicao malformada ou
 * chave invalida nao melhoram por insistencia.
 */
@Slf4j
public class AnthropicClient {

    private static final String CAMINHO_MENSAGENS = "/v1/messages";
    private static final String CABECALHO_CHAVE = "x-api-key";
    private static final String CABECALHO_VERSAO = "anthropic-version";
    private static final String VERSAO_DA_API = "2023-06-01";

    /** Conectar e barato; o que demora e o modelo pensar. */
    private static final Duration TIMEOUT_DE_CONEXAO = Duration.ofSeconds(10);

    private static final Duration ESPERA_INICIAL = Duration.ofMillis(500);

    private final RestClient rest;
    private final AgenteProperties propriedades;

    public AnthropicClient(RestClient rest, AgenteProperties propriedades) {
        this.rest = rest;
        this.propriedades = propriedades;
    }

    /** Endereco e credenciais. Nao toca no transporte. */
    public static RestClient.Builder identificar(RestClient.Builder builder,
                                                 AgenteProperties propriedades) {
        return builder
                .baseUrl(propriedades.urlBase())
                .defaultHeader(CABECALHO_CHAVE,
                        propriedades.chaveApi() == null ? "" : propriedades.chaveApi())
                .defaultHeader(CABECALHO_VERSAO, VERSAO_DA_API);
    }

    /**
     * O RestClient de producao: identidade mais transporte com timeout.
     *
     * <p>As duas metades sao separadas para que o teste monte a sua com o stub no
     * lugar do transporte, sem perder os cabecalhos — que o teste tambem precisa
     * verificar. Um stub que nao exige a chave nao provaria nada sobre como
     * autenticamos.
     */
    public static RestClient configurar(RestClient.Builder builder, AgenteProperties propriedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT_DE_CONEXAO);
        fabrica.setReadTimeout(propriedades.timeout());

        return identificar(builder, propriedades).requestFactory(fabrica).build();
    }

    /**
     * Uma volta de conversa com o modelo.
     *
     * @throws FalhaNaApiException quando a API nao responde util depois das
     *                             retentativas previstas
     */
    public ProtocoloAnthropic.Resposta enviar(ProtocoloAnthropic.Requisicao requisicao) {
        if (!propriedades.disponivel()) {
            throw new AgenteDesligadoException();
        }

        RuntimeException ultimaFalha = null;

        for (int tentativa = 0; tentativa <= propriedades.maxRetentativas(); tentativa++) {
            if (tentativa > 0) {
                esperar(ESPERA_INICIAL.multipliedBy(1L << (tentativa - 1)));
            }
            try {
                ProtocoloAnthropic.Resposta resposta = rest.post()
                        .uri(CAMINHO_MENSAGENS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requisicao)
                        .retrieve()
                        .body(ProtocoloAnthropic.Resposta.class);

                if (resposta == null) {
                    throw new FalhaNaApiException("Resposta vazia da API de mensagens");
                }
                return resposta;

            } catch (RestClientResponseException ex) {
                if (!valeRetentar(ex.getStatusCode())) {
                    // o corpo do erro pode conter a chave ecoada: so o status vai para o log
                    log.warn("API de mensagens recusou a requisicao: HTTP {}", ex.getStatusCode());
                    throw new FalhaNaApiException("API de mensagens respondeu " + ex.getStatusCode(), ex);
                }
                log.warn("API de mensagens instavel (HTTP {}), tentativa {} de {}",
                        ex.getStatusCode(), tentativa + 1, propriedades.maxRetentativas() + 1);
                ultimaFalha = ex;

            } catch (ResourceAccessException ex) {
                log.warn("API de mensagens nao respondeu em {}s", propriedades.timeout().toSeconds());
                throw new FalhaNaApiException("Tempo esgotado na API de mensagens", ex);
            }
        }

        throw new FalhaNaApiException("API de mensagens indisponivel apos as retentativas", ultimaFalha);
    }

    /** Excesso de requisicoes e falha do lado deles: os dois passam. */
    private boolean valeRetentar(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private void esperar(Duration espera) {
        try {
            Thread.sleep(espera.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FalhaNaApiException("Espera entre retentativas interrompida", ex);
        }
    }
}
