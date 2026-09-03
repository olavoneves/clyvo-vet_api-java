package br.com.clyvovet.server.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Cache em memoria do catalogo de protocolos.
 *
 * <p>Caffeine e nao Redis: a demonstracao nao pode depender de um servico
 * externo de pe. O catalogo muda em escala de meses e e lido em toda leitura de
 * obrigacao, entao uma hora de validade troca muita consulta repetida por um
 * atraso irrelevante para propagar mudanca de protocolo.
 *
 * <p>O recorte por clinica <b>nao</b> vem de graca aqui. O filtro de tenant do
 * Hibernate age na consulta; o cache guarda o resultado dela. Guardar por id do
 * protocolo apenas entregaria a clinica B o protocolo carregado para a A — por
 * isso toda chave em {@code ProtocoloService} comeca pelo TenantContext.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Catalogo de protocolo: protocolos, versoes e etapas. */
    public static final String CATALOGO_DE_PROTOCOLOS = "catalogoDeProtocolos";

    private static final Duration VALIDADE = Duration.ofHours(1);
    private static final int TETO_DE_ENTRADAS = 1_000;

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager gerenciador = new CaffeineCacheManager(CATALOGO_DE_PROTOCOLOS);
        gerenciador.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(VALIDADE.toMinutes(), TimeUnit.MINUTES)
                .maximumSize(TETO_DE_ENTRADAS));
        return gerenciador;
    }
}
