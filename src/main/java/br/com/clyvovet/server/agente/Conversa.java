package br.com.clyvovet.server.agente;

import java.util.ArrayList;
import java.util.List;

/**
 * O historico de um tutor sobre um pet, no formato que a API entende.
 *
 * <p>Guardar as mensagens ja no formato de fio evita manter duas representacoes
 * da mesma conversa. A visao legivel — o que o endpoint de historico devolve — e
 * derivada daqui filtrando os blocos que so interessam ao modelo.
 */
public class Conversa {

    /**
     * Quantos turnos completos ficam no contexto.
     *
     * <p>Um turno de agendamento gasta varias mensagens: o pedido, a chamada de
     * ferramenta, o resultado, a resposta. Sem poda, uma conversa de um dia
     * inteiro passaria a custar mais em prompt do que vale em utilidade.
     */
    private static final int MAX_TURNOS = 12;

    private final List<ProtocoloAnthropic.Mensagem> mensagens = new ArrayList<>();
    private boolean escalada;

    public List<ProtocoloAnthropic.Mensagem> mensagens() {
        return List.copyOf(mensagens);
    }

    public boolean escalada() {
        return escalada;
    }

    public void marcarEscalada() {
        this.escalada = true;
    }

    public void adicionar(ProtocoloAnthropic.Mensagem mensagem) {
        mensagens.add(mensagem);
    }

    /**
     * Troca a ultima fala do agente pelo texto que o tutor de fato recebeu.
     *
     * <p>Chamado quando o guardrail barra a resposta. Sem isto, o texto proibido
     * continuaria no historico e voltaria para a API na mensagem seguinte — o
     * modelo leria a propria opiniao clinica como precedente aceito e seguiria
     * dali. A barragem tem que valer para a conversa, nao so para a tela.
     */
    public void substituirUltimaRespostaDoAgente(String texto) {
        for (int i = mensagens.size() - 1; i >= 0; i--) {
            if (ProtocoloAnthropic.PAPEL_AGENTE.equals(mensagens.get(i).role())) {
                mensagens.set(i, ProtocoloAnthropic.Mensagem.doAgente(
                        List.of(ProtocoloAnthropic.Bloco.texto(texto))));
                return;
            }
        }
    }

    /**
     * Descarta os turnos mais antigos.
     *
     * <p>Corta so no inicio de um turno — a mensagem do tutor que o abriu. Cortar
     * em qualquer outro ponto deixaria um {@code tool_use} sem o
     * {@code tool_result} correspondente, e a API recusa a requisicao inteira
     * quando isso acontece.
     */
    public void podar() {
        List<Integer> inicios = new ArrayList<>();
        for (int i = 0; i < mensagens.size(); i++) {
            if (ehInicioDeTurno(mensagens.get(i))) {
                inicios.add(i);
            }
        }
        if (inicios.size() <= MAX_TURNOS) {
            return;
        }
        int corte = inicios.get(inicios.size() - MAX_TURNOS);
        mensagens.subList(0, corte).clear();
    }

    /** Mensagem do tutor com texto de verdade, e nao um lote de resultados de ferramenta. */
    private static boolean ehInicioDeTurno(ProtocoloAnthropic.Mensagem mensagem) {
        return ProtocoloAnthropic.PAPEL_TUTOR.equals(mensagem.role())
                && mensagem.content() != null
                && mensagem.content().stream().anyMatch(ProtocoloAnthropic.Bloco::ehTexto);
    }
}
