package br.com.clyvovet.server.agente;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * O formato de fio da API de mensagens da Anthropic, so o que este agente usa.
 *
 * <p>Records em vez de mapas soltos porque o laco de execucao le
 * {@code stop_reason} e o conteudo de cada bloco a cada volta: com mapa, um
 * campo renomeado viraria NullPointerException em producao em vez de erro de
 * compilacao.
 *
 * <p>{@code NON_NULL} em toda parte nao e cosmetica. Um bloco de texto e um
 * bloco de uso de ferramenta sao o mesmo record aqui, com campos diferentes
 * preenchidos; serializar os nulos mandaria {@code "tool_use_id": null} num
 * bloco de texto, que a API recusa.
 */
public final class ProtocoloAnthropic {

    private ProtocoloAnthropic() {
    }

    /** Papeis aceitos em {@code messages}. */
    public static final String PAPEL_TUTOR = "user";
    public static final String PAPEL_AGENTE = "assistant";

    /** Tipos de bloco de conteudo. */
    public static final String BLOCO_TEXTO = "text";
    public static final String BLOCO_USO_DE_FERRAMENTA = "tool_use";
    public static final String BLOCO_RESULTADO_DE_FERRAMENTA = "tool_result";

    /** O modelo pediu uma ferramenta e espera o resultado para continuar. */
    public static final String PARADA_POR_FERRAMENTA = "tool_use";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Requisicao(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<Mensagem> messages,
            List<Ferramenta> tools
    ) {}

    /**
     * Declaracao de uma ferramenta para o modelo.
     *
     * <p>{@code strict} obriga a API a entregar {@code input} validado contra o
     * esquema. Sem isso o agente precisaria tratar argumento faltando ou de tipo
     * errado a cada execucao — e um id de pet que chega como texto vira consulta
     * quebrada, nao mensagem de erro compreensivel.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ferramenta(
            String name,
            String description,
            @JsonProperty("input_schema") Map<String, Object> inputSchema,
            Boolean strict
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mensagem(String role, List<Bloco> content) {

        public static Mensagem doTutor(List<Bloco> blocos) {
            return new Mensagem(PAPEL_TUTOR, blocos);
        }

        public static Mensagem doTutor(String texto) {
            return doTutor(List.of(Bloco.texto(texto)));
        }

        public static Mensagem doAgente(List<Bloco> blocos) {
            return new Mensagem(PAPEL_AGENTE, blocos);
        }
    }

    /**
     * Um bloco de conteudo. Os tres tipos que o agente troca com a API cabem no
     * mesmo record: cada um preenche seus campos e deixa o resto nulo.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bloco(
            String type,
            String text,
            String id,
            String name,
            Map<String, Object> input,
            @JsonProperty("tool_use_id") String toolUseId,
            String content
    ) {

        public static Bloco texto(String texto) {
            return new Bloco(BLOCO_TEXTO, texto, null, null, null, null, null);
        }

        public static Bloco resultado(String idDoUso, String conteudo) {
            return new Bloco(BLOCO_RESULTADO_DE_FERRAMENTA, null, null, null, null, idDoUso, conteudo);
        }

        public boolean ehTexto() {
            return BLOCO_TEXTO.equals(type);
        }

        public boolean ehUsoDeFerramenta() {
            return BLOCO_USO_DE_FERRAMENTA.equals(type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resposta(
            String id,
            String model,
            List<Bloco> content,
            @JsonProperty("stop_reason") String stopReason
    ) {

        public boolean pediuFerramenta() {
            return PARADA_POR_FERRAMENTA.equals(stopReason);
        }

        /** Blocos de texto concatenados: e o que o tutor le. */
        public String textoConcatenado() {
            return content == null ? "" : content.stream()
                    .filter(Bloco::ehTexto)
                    .map(Bloco::text)
                    .filter(t -> t != null && !t.isBlank())
                    .reduce((a, b) -> a + "\n\n" + b)
                    .orElse("");
        }

        public List<Bloco> usosDeFerramenta() {
            return content == null ? List.of() : content.stream().filter(Bloco::ehUsoDeFerramenta).toList();
        }
    }
}
