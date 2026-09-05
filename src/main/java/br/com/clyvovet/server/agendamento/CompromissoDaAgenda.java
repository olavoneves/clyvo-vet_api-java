package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
import br.com.clyvovet.server.enums.CanalPreferencial;
import br.com.clyvovet.server.enums.ObrigacaoStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Uma linha da agenda da clinica: um compromisso marcado, no dia em que ele
 * acontece.
 *
 * <p>Existe porque a agenda operacional e sobre <b>quem vem</b>, e nao sobre
 * <b>o que venceu</b>. A pagina listava obrigacoes pela data prevista, e com
 * isso o caso que mais importa ao produto sumia da tela: a obrigacao que venceu
 * atrasada e foi remarcada para a semana que vem nao aparecia nem no dia previsto
 * — ja passado — nem no dia da consulta. Justamente a receita sendo recuperada.
 *
 * <p>Os campos da obrigacao sao nulos quando o compromisso nasceu fora do motor:
 * marcado pelo balcao, sem protocolo atras. A agenda mostra os dois.
 */
public record CompromissoDaAgenda(
        Long idAgendamento,
        LocalDate data,
        String hora,
        AgendamentoStatus status,
        CanalPreferencial canalOrigem,
        Long petId,
        String petNome,
        String veterinarioNome,
        String etapaNome,
        String protocoloNome,
        LocalDate dtPrevista,
        ObrigacaoStatus statusObrigacao
) {

    /**
     * O compromisso recupera uma obrigacao que ja tinha vencido.
     *
     * <p>Comparar com a data marcada, e nao com hoje: o que caracteriza a
     * recuperacao e ter sido remarcado <i>para depois</i> do vencimento, e isso
     * continua verdade quando a consulta finalmente chega.
     */
    public boolean recuperaAtraso() {
        return dtPrevista != null && dtPrevista.isBefore(data);
    }

    /** Quantos dias entre o vencimento e o compromisso. Zero se nao ha atraso. */
    public long diasDeAtraso() {
        return recuperaAtraso() ? ChronoUnit.DAYS.between(dtPrevista, data) : 0;
    }

    /** O compromisso nasceu do motor de protocolo, e nao do balcao. */
    public boolean veioDeObrigacao() {
        return dtPrevista != null;
    }
}
