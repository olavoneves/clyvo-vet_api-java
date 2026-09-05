package br.com.clyvovet.server.agente.llm;

import java.util.List;
import java.util.Map;

/**
 * O vocabulario de uma conversa com um modelo, sem provedor nenhum atras.
 *
 * <p>E o contrato que {@link ProvedorLlm} troca com o laco do agente. Nada aqui
 * conhece {@code stop_reason}, {@code functionCall} ou qualquer nome de campo de
 * fornecedor: cada adaptador traduz deste vocabulario para o seu formato e de
 * volta. O laco, a memoria de conversa e o catalogo de ferramentas falam so
 * isto, e por isso nao mudam quando o provedor muda.
 *
 * <p>O ponto sutil e o {@link Bloco.Chamada#id()}. A Anthropic exige que o
 * resultado ecoe o {@code tool_use_id} que ela emitiu; o Gemini casa chamada e
 * resposta pelo <i>nome</i> da funcao e nao emite id nenhum. Carregar os dois
 * campos — id e nome — em {@link Bloco.Resultado} e o que permite que o mesmo
 * historico sirva aos dois sem o laco saber de qual se trata; para o Gemini o id
 * e sintetico e simplesmente ignorado na serializacao.
 */
public final class DialogoLlm {

    private DialogoLlm() {
    }

    /** Quem fala. O tutor tambem "fala" ao devolver resultado de ferramenta. */
    public enum Papel {
        TUTOR, AGENTE
    }

    /**
     * Um pedaco de uma mensagem.
     *
     * <p>Selada de proposito: os tres casos sao o universo fechado do que este
     * agente troca com um modelo, e o compilador passa a cobrar o tratamento de
     * todos eles em cada {@code switch} — inclusive nos adaptadores que ainda nao
     * existem.
     */
    public sealed interface Bloco {

        /** Texto corrido: a pergunta do tutor ou a resposta do modelo. */
        record Texto(String texto) implements Bloco {
        }

        /** O modelo quer que uma ferramenta seja executada. */
        record Chamada(String id, String nome, Map<String, Object> argumentos) implements Bloco {

            public Chamada {
                argumentos = argumentos == null ? Map.of() : Map.copyOf(argumentos);
            }
        }

        /**
         * O que a ferramenta devolveu, ainda como objeto de dominio.
         *
         * <p>Nao serializado de proposito. A Anthropic quer o resultado como
         * <i>texto</i> no campo {@code content}; o Gemini quer um <i>objeto</i>
         * JSON em {@code functionResponse.response}. Entregar uma string pronta
         * obrigaria um dos dois adaptadores a desfazer o trabalho do outro.
         */
        record Resultado(String id, String nome, Object conteudo) implements Bloco {
        }
    }

    /** Uma fala completa, de um lado ou do outro. */
    public record Mensagem(Papel papel, List<Bloco> blocos) {

        public Mensagem {
            blocos = blocos == null ? List.of() : List.copyOf(blocos);
        }

        public static Mensagem doTutor(String texto) {
            return new Mensagem(Papel.TUTOR, List.of(new Bloco.Texto(texto)));
        }

        public static Mensagem doTutor(List<Bloco> blocos) {
            return new Mensagem(Papel.TUTOR, blocos);
        }

        public static Mensagem doAgente(List<Bloco> blocos) {
            return new Mensagem(Papel.AGENTE, blocos);
        }

        public static Mensagem doAgente(String texto) {
            return doAgente(List.of(new Bloco.Texto(texto)));
        }

        /** Os textos desta mensagem, que e o que uma pessoa leria dela. */
        public List<String> textos() {
            return blocos.stream()
                    .filter(Bloco.Texto.class::isInstance)
                    .map(bloco -> ((Bloco.Texto) bloco).texto())
                    .filter(texto -> texto != null && !texto.isBlank())
                    .toList();
        }
    }

    /**
     * Uma ferramenta como o modelo precisa ve-la.
     *
     * <p>Sem {@code strict}: e um recurso da Anthropic, e quem o liga e o
     * adaptador dela. Uma flag de fornecedor no vocabulario comum seria a
     * primeira rachadura da porta.
     */
    public record Ferramenta(String nome, String descricao, Map<String, Object> esquema) {
    }

    /**
     * O que o modelo devolveu numa volta: ou o texto final, ou chamadas de
     * ferramenta.
     *
     * <p>Os dois campos coexistem porque alguns modelos falam antes de chamar a
     * ferramenta — o texto acompanha as chamadas. Quem decide se o turno acabou e
     * {@link #pediuFerramenta()}, nunca a presenca de texto.
     */
    public record Turno(String texto, List<Bloco.Chamada> chamadas) {

        public Turno {
            texto = texto == null ? "" : texto;
            chamadas = chamadas == null ? List.of() : List.copyOf(chamadas);
        }

        public static Turno deTexto(String texto) {
            return new Turno(texto, List.of());
        }

        public static Turno deChamadas(String texto, List<Bloco.Chamada> chamadas) {
            return new Turno(texto, chamadas);
        }

        public boolean pediuFerramenta() {
            return !chamadas.isEmpty();
        }

        /** Os blocos que entram no historico como a fala do agente nesta volta. */
        public List<Bloco> comoBlocos() {
            List<Bloco> blocos = new java.util.ArrayList<>();
            if (!texto.isBlank()) {
                blocos.add(new Bloco.Texto(texto));
            }
            blocos.addAll(chamadas);
            return List.copyOf(blocos);
        }
    }
}
