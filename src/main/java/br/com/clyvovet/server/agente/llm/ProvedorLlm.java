package br.com.clyvovet.server.agente.llm;

import java.util.List;

/**
 * A porta de saida do agente para um modelo de linguagem.
 *
 * <p>Existe para que trocar de fornecedor seja escrever uma classe, e nao
 * reescrever o modulo. O laco em {@code AgenteService}, a memoria de conversa e
 * o catalogo de ferramentas nao mencionam nenhum provedor; tudo que e especifico
 * — caminho do endpoint, nome dos campos, cabecalho da chave, formato da chamada
 * de ferramenta — vive dentro de uma implementacao desta interface.
 *
 * <p>A motivacao e concreta e vale registrar: o provedor ativo roda em tier
 * gratuito, e tier gratuito muda de limite e de disponibilidade sem avisar.
 * Quando isso acontecer, a resposta e trocar o valor de
 * {@code app.agente.provedor} — nao abrir o laco de execucao.
 *
 * <p><b>O guardrail clinico nao passa por aqui, e isso e deliberado.</b> Ele le
 * a resposta final depois que o adaptador ja devolveu, no
 * {@code AgenteService}. Se cada adaptador fosse responsavel por aplica-lo, um
 * provedor novo poderia esquecer — e a camada que existe justamente porque o
 * modelo erra passaria a depender de qual modelo esta atras.
 */
public interface ProvedorLlm {

    /** Como este provedor aparece no log e no registro da conversa. */
    String nome();

    /**
     * Se ha credencial para chamar este provedor.
     *
     * <p>Mora na porta, e nao nas propriedades, porque cada provedor tem a sua
     * chave e a sua variavel de ambiente. Quem pergunta — o laco, antes de
     * qualquer trabalho — nao precisa saber qual delas esta em jogo.
     */
    boolean disponivel();

    /**
     * Uma volta de conversa.
     *
     * @param promptDoSistema instrucoes fixas do turno, ja montadas
     * @param historico       a conversa ate aqui, do primeiro turno ao ultimo
     * @param ferramentas     o que o modelo pode chamar nesta volta
     * @return texto final, ou as ferramentas que o modelo quer executar
     * @throws br.com.clyvovet.server.agente.FalhaNaApiException quando a API nao
     *         responde util depois das retentativas previstas
     * @throws br.com.clyvovet.server.agente.AgenteDesligadoException se nao ha chave
     */
    DialogoLlm.Turno responder(String promptDoSistema,
                               List<DialogoLlm.Mensagem> historico,
                               List<DialogoLlm.Ferramenta> ferramentas);
}
