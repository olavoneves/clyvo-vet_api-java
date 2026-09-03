package br.com.clyvovet.server.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Limite de requisicoes por IP, em memoria.
 *
 * <p>Roda antes da cadeia do Spring Security de proposito: se o limite so
 * valesse depois da autenticacao, ele nao protegeria justamente a rota que mais
 * precisa — quem tenta adivinhar senha nunca chega a se autenticar.
 *
 * <p>Os baldes ficam num cache Caffeine com expiracao por acesso, e nao num
 * mapa comum: um mapa indexado por IP cresceria para sempre, e um scanner
 * batendo de milhares de enderecos viraria um vazamento de memoria.
 *
 * <p>O refil e por intervalo, nao continuo: "5 por minuto" devolve as 5 fichas
 * de uma vez a cada minuto, que e como a regra e lida por quem a configurou.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration JANELA = Duration.ofMinutes(1);
    private static final Duration OCIOSIDADE_ATE_ESQUECER = Duration.ofMinutes(10);
    private static final int TETO_DE_IPS = 50_000;

    private final RateLimitProperties propriedades;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> baldes;

    public RateLimitFilter(RateLimitProperties propriedades, ObjectMapper objectMapper) {
        this.propriedades = propriedades;
        this.objectMapper = objectMapper;
        this.baldes = Caffeine.newBuilder()
                .expireAfterAccess(OCIOSIDADE_ATE_ESQUECER.toMinutes(), TimeUnit.MINUTES)
                .maximumSize(TETO_DE_IPS)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // health check do container nao pode ser barrado: um pico de trafego
        // derrubaria a instancia justamente quando ela esta sob carga
        return !propriedades.habilitado()
                || request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean autenticacao = ehRotaDeAutenticacao(request.getRequestURI());
        int teto = autenticacao ? propriedades.autenticacaoPorMinuto() : propriedades.padraoPorMinuto();
        String chave = (autenticacao ? "auth|" : "geral|") + ipDeOrigem(request);

        ConsumptionProbe tentativa = baldes
                .get(chave, ignorado -> novoBalde(teto))
                .tryConsumeAndReturnRemaining(1);

        if (tentativa.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(tentativa.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long segundos = Math.max(1, Duration.ofNanos(tentativa.getNanosToWaitForRefill()).toSeconds());
        log.warn("Rate limit atingido em {} ({} req/min)", request.getRequestURI(), teto);
        responderExcedido(response, segundos, teto);
    }

    private Bucket novoBalde(int teto) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(teto).refillIntervally(teto, JANELA).build())
                .build();
    }

    private boolean ehRotaDeAutenticacao(String uri) {
        return propriedades.rotasDeAutenticacao().contains(uri);
    }

    /**
     * Em producao a aplicacao fica atras do proxy da plataforma, entao o IP da
     * conexao e sempre o mesmo e todos os clientes dividiriam um unico balde.
     * O cabecalho de encaminhamento e a unica forma de separa-los — e por isso
     * mesmo e configuravel: onde nao houver proxy confiavel na frente, deixar a
     * propriedade vazia evita que o cliente escolha o proprio identificador.
     */
    private String ipDeOrigem(HttpServletRequest request) {
        String cabecalho = propriedades.cabecalhoDeIp();
        if (StringUtils.hasText(cabecalho)) {
            String encaminhado = request.getHeader(cabecalho);
            if (StringUtils.hasText(encaminhado)) {
                return encaminhado.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void responderExcedido(HttpServletResponse response, long segundos, int teto)
            throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Limite de %d requisicoes por minuto atingido. Tente novamente em %d s."
                        .formatted(teto, segundos));
        problema.setType(URI.create("https://clinicavet.com/errors/rate-limit"));
        problema.setTitle("Too Many Requests");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problema);
    }
}
