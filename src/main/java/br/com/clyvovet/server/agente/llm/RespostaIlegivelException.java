package br.com.clyvovet.server.agente.llm;

import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A resposta chegou inteira, com HTTP 200, e mesmo assim nao serve ao laco:
 * JSON truncado ou malformado, ou nenhum conteudo no primeiro candidato.
 *
 * <p>A mensagem e o diagnostico que vai para o WARN do {@link ChamadaResiliente},
 * e por isso e montada com cuidado: o corpo carrega o texto do modelo e os
 * argumentos das ferramentas, que falam do tutor e do pet. Do corpo saem so o
 * {@code finishReason}, o {@code blockReason}, o tamanho e um trecho com todo
 * valor de texto mascarado — a estrutura, e nunca o conteudo.
 */
final class RespostaIlegivelException extends RestClientException {

    /** Metade do trecho vem do comeco (pagina de erro), metade do fim (truncamento). */
    private static final int MEIO_TRECHO = 150;

    private static final Pattern FINISH_REASON = Pattern.compile("\"finishReason\"\\s*:\\s*\"(\\w+)\"");
    private static final Pattern BLOCK_REASON = Pattern.compile("\"blockReason\"\\s*:\\s*\"(\\w+)\"");

    RespostaIlegivelException(String motivo, byte[] corpo) {
        super(motivo + ": " + diagnostico(corpo));
    }

    static String diagnostico(byte[] corpo) {
        String texto = new String(corpo, StandardCharsets.UTF_8);
        return "finishReason=%s blockReason=%s bytes=%d trecho=%s".formatted(
                procurar(FINISH_REASON, texto), procurar(BLOCK_REASON, texto),
                corpo.length, trecho(mascarar(texto)));
    }

    private static String procurar(Pattern padrao, String texto) {
        Matcher achado = padrao.matcher(texto);
        return achado.find() ? achado.group(1) : "-";
    }

    private static String trecho(String texto) {
        String linha = texto.replaceAll("\\s+", " ");
        if (linha.length() <= 2 * MEIO_TRECHO) {
            return linha;
        }
        return linha.substring(0, MEIO_TRECHO) + " [...] " + linha.substring(linha.length() - MEIO_TRECHO);
    }

    /**
     * Troca todo valor de texto do JSON por {@code "..."}, mantendo as chaves.
     *
     * <p>Um varredor, e nao uma expressao regular, porque o corpo pode estar
     * cortado no meio de uma string: a ultima aspa fica sem par, e o que vem
     * depois dela e justamente o texto do modelo.
     */
    static String mascarar(String json) {
        StringBuilder saida = new StringBuilder(json.length());
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c != '"') {
                saida.append(c);
                i++;
                continue;
            }
            int fim = i + 1;
            while (fim < json.length() && json.charAt(fim) != '"') {
                fim += json.charAt(fim) == '\\' ? 2 : 1;
            }
            if (fim >= json.length()) {
                saida.append("\"...");
                break;
            }
            if (eChave(json, fim + 1)) {
                saida.append(json, i, fim + 1);
            } else {
                saida.append("\"...\"");
            }
            i = fim + 1;
        }
        return saida.toString();
    }

    private static boolean eChave(String json, int depoisDaAspa) {
        int i = depoisDaAspa;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        return i < json.length() && json.charAt(i) == ':';
    }
}
