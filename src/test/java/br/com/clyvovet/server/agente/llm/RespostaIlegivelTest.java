package br.com.clyvovet.server.agente.llm;

import br.com.clyvovet.server.agente.AgenteProperties;
import br.com.clyvovet.server.agente.FalhaNaApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * O que o WARN diz quando o Gemini responde 200 com algo que o laco nao usa.
 *
 * <p>O ERROR de 29/10 ("Resposta ilegivel de gemini") nao dizia o que tinha
 * vindo. Estes testes fixam o diagnostico — finishReason, blockReason e um
 * trecho do corpo — e que nada do que o modelo escreveu sobre o tutor ou o pet
 * vai junto.
 */
class RespostaIlegivelTest {

    private static final String STUB = "https://stub.gemini.local";
    private static final String MODELO = "gemini-3.1-flash-lite";
    private static final String ROTA = STUB + "/v1beta/models/" + MODELO + ":generateContent";

    private MockRestServiceServer api;
    private GeminiAdapter gemini;

    @BeforeEach
    void montar() {
        AgenteProperties propriedades = new AgenteProperties(6, 8192, Duration.ofSeconds(30), 2,
                Duration.ofHours(24), null, new AgenteProperties.Provedor("chave-de-teste", MODELO, STUB));
        RestClient.Builder builder = RestClient.builder();
        api = MockRestServiceServer.bindTo(builder).build();
        gemini = new GeminiAdapter(GeminiAdapter.identificar(builder, propriedades).build(), propriedades);
    }

    @Test
    @DisplayName("JSON truncado: duas tentativas, e o WARN leva estrutura sem o texto do modelo")
    void jsonTruncadoNaoVazaOTexto() {
        api.expect(ExpectedCount.twice(), requestTo(ROTA)).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Camila, o Thor tem hor",
                MediaType.APPLICATION_JSON));

        String diagnostico = falhaAoResponder().getCause().getMessage();

        api.verify();
        assertThat(diagnostico)
                .startsWith("JSON invalido: finishReason=- blockReason=- bytes=")
                .contains("\"candidates\":[{\"content\":{\"role\":\"...\",\"parts\":[{\"text\":\"...")
                .doesNotContain("Camila", "Thor");
    }

    @Test
    @DisplayName("prompt bloqueado: o blockReason aparece no WARN")
    void promptBloqueadoMostraOMotivo() {
        api.expect(ExpectedCount.twice(), requestTo(ROTA)).andRespond(withSuccess(
                "{\"promptFeedback\":{\"blockReason\":\"PROHIBITED_CONTENT\"},\"modelVersion\":\"x\"}",
                MediaType.APPLICATION_JSON));

        FalhaNaApiException falha = falhaAoResponder();

        assertThat(falha).hasMessage("Resposta ilegivel de gemini");
        assertThat(falha.getCause().getMessage())
                .startsWith("Resposta sem conteudo: finishReason=- blockReason=PROHIBITED_CONTENT");
    }

    @Test
    @DisplayName("candidato sem partes (MALFORMED_FUNCTION_CALL) e retentado uma vez")
    void candidatoSemPartesERetentado() {
        api.expect(ExpectedCount.once(), requestTo(ROTA)).andRespond(withSuccess(
                "{\"candidates\":[{\"finishReason\":\"MALFORMED_FUNCTION_CALL\"}]}",
                MediaType.APPLICATION_JSON));
        api.expect(ExpectedCount.once(), requestTo(ROTA)).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Oi.\"}]},"
                        + "\"finishReason\":\"STOP\"}]}",
                MediaType.APPLICATION_JSON));

        assertThat(gemini.responder("prompt", List.of(), List.of()).texto()).isEqualTo("Oi.");
        api.verify();
    }

    /**
     * O timeout que estoura no meio do corpo chega como RestClientException, igual
     * a uma resposta ilegivel. Ele nao pode ganhar a nova tentativa: seriam mais
     * 60 segundos com o tutor olhando a tela.
     */
    @Test
    @DisplayName("timeout durante a leitura do corpo continua sem retentativa")
    void timeoutNaLeituraNaoERetentado() {
        api.expect(ExpectedCount.once(), requestTo(ROTA)).andRespond(requisicao -> {
            MockClientHttpResponse resposta = new MockClientHttpResponse(new InputStream() {
                @Override
                public int read() throws SocketTimeoutException {
                    throw new SocketTimeoutException("Read timed out");
                }
            }, HttpStatus.OK);
            resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return resposta;
        });

        FalhaNaApiException falha = falhaAoResponder();

        api.verify();
        assertThat(falha).hasMessage("Tempo esgotado em gemini");
    }

    @Test
    @DisplayName("a mascara troca valores de texto e preserva chaves, numeros e a string cortada")
    void mascaraPreservaSoAEstrutura() {
        assertThat(RespostaIlegivelException.mascarar(
                "{\"args\":{\"idPet\":837,\"nome\":\"Thor \\\"o bravo\\\"\"},\"x\":[\"a\",\"b\"],\"t\":\"Cam"))
                .isEqualTo("{\"args\":{\"idPet\":837,\"nome\":\"...\"},\"x\":[\"...\",\"...\"],\"t\":\"...");
    }

    private FalhaNaApiException falhaAoResponder() {
        return catchThrowableOfType(FalhaNaApiException.class,
                () -> gemini.responder("prompt", List.of(), List.of()));
    }
}
