package br.com.clyvovet.server.agente.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * O formato de fio de {@code generateContent}, so o que este agente usa.
 *
 * <p>Records em vez de mapas soltos pelo mesmo motivo do protocolo da Anthropic:
 * o adaptador le {@code candidates[0].content.parts} a cada volta, e com mapa um
 * campo renomeado viraria NullPointerException em producao em vez de erro de
 * compilacao.
 *
 * <p>Tres diferencas de forma em relacao a Anthropic valem estar escritas, e sao
 * elas que o adaptador existe para absorver:
 *
 * <ul>
 *   <li>O papel do modelo e {@code "model"}, nao {@code "assistant"}.
 *   <li>Nao ha {@code stop_reason} indicando uso de ferramenta. O que diz que o
 *       modelo quer uma ferramenta e a <i>presenca</i> de uma parte
 *       {@code functionCall} — por isso o adaptador olha as partes, e nunca o
 *       {@code finishReason}.
 *   <li>Chamada e resposta de ferramenta casam pelo <b>nome</b> da funcao. Nao
 *       existe id no fio, e o id do vocabulario neutro nao e serializado aqui.
 * </ul>
 */
public final class ProtocoloGemini {

    private ProtocoloGemini() {
    }

    /** Papeis aceitos em {@code contents}. */
    public static final String PAPEL_TUTOR = "user";
    public static final String PAPEL_MODELO = "model";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Requisicao(
            Conteudo systemInstruction,
            List<Conteudo> contents,
            List<Ferramentas> tools,
            ConfiguracaoDeGeracao generationConfig
    ) {}

    /** O envelope que o Gemini exige em volta da lista de declaracoes. */
    public record Ferramentas(List<Declaracao> functionDeclarations) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Declaracao(String name, String description, Map<String, Object> parameters) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConfiguracaoDeGeracao(Integer maxOutputTokens) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Conteudo(String role, List<Parte> parts) {

        public static Conteudo de(String papel, List<Parte> partes) {
            return new Conteudo(papel, partes);
        }

        /** O prompt do sistema vai sem papel: e um Content, mas nao e um turno. */
        public static Conteudo instrucao(String texto) {
            return new Conteudo(null, List.of(Parte.texto(texto)));
        }
    }

    /**
     * Uma parte de um {@code Content}. Como no protocolo da Anthropic, os tres
     * casos cabem no mesmo record: cada um preenche o seu campo e deixa o resto
     * nulo, e {@code NON_NULL} impede que os nulos vao para o fio.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parte(String text, ChamadaDeFuncao functionCall, RespostaDeFuncao functionResponse,
                        String thoughtSignature) {

        public static Parte texto(String texto) {
            return new Parte(texto, null, null, null);
        }

        /**
         * A chamada de ferramenta, com a assinatura que a acompanhava.
         *
         * <p>O {@code thoughtSignature} e irmao do {@code functionCall} dentro da
         * mesma parte, e nao um campo dele. Devolver a chamada sem ele faz a
         * familia 3.x recusar a requisicao inteira com 400
         * ({@code Function call is missing a thought_signature}), entao o que o
         * modelo emitiu numa volta tem que reaparecer identico na seguinte.
         */
        public static Parte chamada(ChamadaDeFuncao chamada, String assinatura) {
            return new Parte(null, chamada, null, assinatura);
        }

        public static Parte resposta(String nome, Object conteudo) {
            return new Parte(null, null, new RespostaDeFuncao(nome, Map.of("resultado", conteudo)), null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChamadaDeFuncao(String name, Map<String, Object> args) {}

    /**
     * O resultado devolvido ao modelo.
     *
     * <p>{@code response} tem que ser um objeto JSON — o Gemini recusa um valor
     * escalar aqui. Como uma ferramenta pode devolver uma lista (a agenda livre,
     * por exemplo), o conteudo vai sempre embrulhado sob uma chave, e nunca
     * direto.
     */
    public record RespostaDeFuncao(String name, Map<String, Object> response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resposta(List<Candidato> candidates, String modelVersion) {

        /** As partes do primeiro candidato, ou vazio se a resposta veio sem nenhum. */
        public List<Parte> partes() {
            if (candidates == null || candidates.isEmpty()) {
                return List.of();
            }
            Conteudo conteudo = candidates.getFirst().content();
            return conteudo == null || conteudo.parts() == null ? List.of() : conteudo.parts();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidato(Conteudo content, String finishReason) {}
}
