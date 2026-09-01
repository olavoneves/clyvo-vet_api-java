package br.com.clyvovet.server.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Limites de requisicao por IP.
 *
 * <p>As rotas de autenticacao levam um limite muito mais apertado que o resto:
 * 100 chamadas por minuto e trafego normal de um app, mas 100 tentativas de
 * senha por minuto e um ataque de forca bruta.
 *
 * @param habilitado             desliga o filtro inteiro, util em teste
 * @param autenticacaoPorMinuto  teto das rotas de login e refresh
 * @param padraoPorMinuto        teto das demais rotas
 * @param rotasDeAutenticacao    caminhos exatos que recebem o limite apertado
 * @param cabecalhoDeIp          cabecalho de proxy que carrega o IP de origem;
 *                               vazio faz o filtro usar o IP da conexao
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean habilitado,
        int autenticacaoPorMinuto,
        int padraoPorMinuto,
        List<String> rotasDeAutenticacao,
        String cabecalhoDeIp
) {

    public RateLimitProperties {
        if (rotasDeAutenticacao == null || rotasDeAutenticacao.isEmpty()) {
            rotasDeAutenticacao = List.of("/api/auth/login", "/api/auth/refresh", "/login");
        }
        if (autenticacaoPorMinuto <= 0) {
            autenticacaoPorMinuto = 5;
        }
        if (padraoPorMinuto <= 0) {
            padraoPorMinuto = 100;
        }
    }
}
