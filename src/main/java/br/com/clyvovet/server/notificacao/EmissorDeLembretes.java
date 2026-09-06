package br.com.clyvovet.server.notificacao;

import br.com.clyvovet.server.enums.CanalPreferencial;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.obrigacao.Obrigacao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Emite o lembrete quando a obrigacao entra em NOTIFICADA.
 *
 * <p><b>E consequencia da transicao, e nao um segundo caminho.</b> Quem chama
 * este emissor e o {@code ObrigacaoService}, no mesmo metodo e na mesma
 * transacao em que a procedure muda o estado. Nao ha rota, comando nem job que
 * emita lembrete por fora: se existisse, alguem acabaria disparando aviso para
 * uma obrigacao que nunca mudou de estado, e a caixa do tutor deixaria de ser
 * um espelho do que o motor decidiu.
 *
 * <p>A recíproca também vale, e é o motivo de a emissão não ser assíncrona: se o
 * lembrete falhar, a transição desfaz junto. Melhor a obrigação continuar
 * NOTIFICADA-pendente e ser reprocessada do que ficar marcada como avisada sem
 * que aviso nenhum exista.
 */
@Slf4j
@Component
public class EmissorDeLembretes {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final NotificacaoRepository repository;
    private final List<CanalDeNotificacao> canais;

    public EmissorDeLembretes(NotificacaoRepository repository, List<CanalDeNotificacao> canais) {
        this.repository = repository;
        this.canais = canais;
    }

    /**
     * Emite, se este for o momento e se ainda nao houver lembrete.
     *
     * <p>Duas guardas, e as duas importam. O estado, porque so NOTIFICADA e
     * lembrete — as outras transicoes tambem passam por aqui. E a existencia,
     * porque a transicao pode se repetir: reprocessamento, correcao manual, o
     * motor rodando de novo. Sem a segunda guarda cada repeticao empilharia um
     * lembrete duplicado na caixa do tutor, e o indice unico da V12 viraria erro
     * em vez de silencio.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoTransitar(Obrigacao obrigacao, ObrigacaoStatus novoStatus) {
        if (novoStatus != ObrigacaoStatus.NOTIFICADA) {
            return;
        }
        if (repository.existsByObrigacaoIdAndCanal(obrigacao.getId(), CanalPreferencial.APP)) {
            return;
        }

        for (CanalDeNotificacao canal : canais) {
            Notificacao notificacao = montar(obrigacao, canal.canal());
            repository.save(notificacao);
            canal.entregar(notificacao);
        }
    }

    private Notificacao montar(Obrigacao obrigacao, CanalPreferencial canal) {
        Notificacao n = new Notificacao();
        n.setClinica(obrigacao.getClinica());
        n.setTutor(obrigacao.getPet().getTutor());
        n.setObrigacao(obrigacao);
        n.setCanal(canal);
        n.setDtEmissao(LocalDateTime.now());
        n.setTitulo(titulo(obrigacao));
        n.setMensagem(mensagem(obrigacao));
        return n;
    }

    /** O que esta sendo cobrado, com o nome do pet: e o que o tutor le na lista. */
    private String titulo(Obrigacao obrigacao) {
        return "%s: %s".formatted(
                obrigacao.getPet().getNome(),
                obrigacao.getEtapa().getNmEtapa());
    }

    /**
     * A mensagem diz a data limite, e nao a data prevista.
     *
     * <p>O tutor nao precisa saber que o protocolo mirava terca: precisa saber
     * ate quando ainda da tempo. Quando a janela ja fechou, dizer "venceu" e mais
     * util do que uma data no passado sem contexto.
     */
    private String mensagem(Obrigacao obrigacao) {
        LocalDate limite = obrigacao.getDtJanelaFim() != null
                ? obrigacao.getDtJanelaFim()
                : obrigacao.getDtPrevista();

        if (limite == null) {
            return "O Clyvo tem um cuidado pendente para o seu pet. Toque para agendar.";
        }
        if (limite.isBefore(LocalDate.now())) {
            return "O prazo venceu em %s. Ainda dá para remarcar — toque para agendar."
                    .formatted(limite.format(DIA));
        }
        return "O prazo vai até %s. Toque para agendar com o assistente."
                .formatted(limite.format(DIA));
    }
}
