package br.com.clyvovet.server.agente.llm;

import br.com.clyvovet.server.agente.FalhaNaApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * A politica de falha de uma chamada a um provedor de LLM.
 *
 * <p>Vive fora dos adaptadores porque nao tem nada de especifico de fornecedor:
 * 429 e 5xx sao transitorios em qualquer API, e a paciencia do tutor esperando
 * uma resposta e a mesma independentemente de quem esta atras. Deixar cada
 * adaptador com a sua copia garantiria que o segundo divergisse do primeiro.
 *
 * <p>Duas politicas, deliberadamente diferentes:
 *
 * <ul>
 *   <li><b>429 e 5xx</b> — a mesma requisicao pode dar certo daqui a pouco.
 *       Retentativa com espera crescente. No tier gratuito o 429 deixa de ser
 *       excecao e vira rotina, o que torna esta metade a mais exercitada das
 *       duas.
 *   <li><b>Timeout</b> — nao e retentado. O limite ja e o teto do que o tutor
 *       aceita esperar; uma segunda tentativa transformaria uma espera ruim numa
 *       inaceitavel. Escala direto.
 * </ul>
 *
 * <p>4xx que nao seja 429 tambem nao e retentado: requisicao malformada ou chave
 * invalida nao melhoram por insistencia.
 */
@Slf4j
final class ChamadaResiliente {

    private static final Duration ESPERA_INICIAL = Duration.ofMillis(500);

    private ChamadaResiliente() {
    }

    /**
     * Executa a chamada aplicando a politica acima.
     *
     * @param provedor nome do provedor, so para o log
     * @param timeout  usado apenas na mensagem: quem o aplica e o transporte
     */
    static <T> T executar(String provedor, int maxRetentativas, Duration timeout,
                          Supplier<T> chamada) {
        RuntimeException ultimaFalha = null;

        for (int tentativa = 0; tentativa <= maxRetentativas; tentativa++) {
            if (tentativa > 0) {
                esperar(ESPERA_INICIAL.multipliedBy(1L << (tentativa - 1)));
            }
            try {
                T resposta = chamada.get();
                if (resposta == null) {
                    throw new FalhaNaApiException("Resposta vazia de " + provedor);
                }
                return resposta;

            } catch (RestClientResponseException ex) {
                if (!valeRetentar(ex.getStatusCode())) {
                    // o corpo do erro pode conter a chave ecoada: so o status vai para o log
                    log.warn("{} recusou a requisicao: HTTP {}", provedor, ex.getStatusCode());
                    throw new FalhaNaApiException(provedor + " respondeu " + ex.getStatusCode(), ex);
                }
                log.warn("{} instavel (HTTP {}), tentativa {} de {}",
                        provedor, ex.getStatusCode(), tentativa + 1, maxRetentativas + 1);
                ultimaFalha = ex;

            } catch (ResourceAccessException ex) {
                log.warn("{} nao respondeu em {}s", provedor, timeout.toSeconds());
                throw new FalhaNaApiException("Tempo esgotado em " + provedor, ex);
            }
        }

        throw new FalhaNaApiException(provedor + " indisponivel apos as retentativas", ultimaFalha);
    }

    /** Excesso de requisicoes e falha do lado deles: os dois passam. */
    private static boolean valeRetentar(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private static void esperar(Duration espera) {
        try {
            Thread.sleep(espera.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FalhaNaApiException("Espera entre retentativas interrompida", ex);
        }
    }
}
