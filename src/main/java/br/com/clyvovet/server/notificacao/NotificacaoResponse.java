package br.com.clyvovet.server.notificacao;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Uma linha da caixa de entrada.
 *
 * <p>Carrega o nome do pet e a data limite em campos proprios, e nao so dentro
 * do texto da mensagem: a tela precisa deles separados para ordenar, destacar o
 * vencido e montar o link — e texto e o unico formato do qual nao se extrai nada
 * sem parsing.
 */
public record NotificacaoResponse(
        Long id,
        String titulo,
        String mensagem,
        String petNome,
        Long petId,
        LocalDate dataLimite,
        LocalDateTime dtEmissao,
        boolean lida
) {

    static NotificacaoResponse de(Notificacao n) {
        var obrigacao = n.getObrigacao();
        return new NotificacaoResponse(
                n.getId(),
                n.getTitulo(),
                n.getMensagem(),
                obrigacao == null ? null : obrigacao.getPet().getNome(),
                obrigacao == null ? null : obrigacao.getPet().getId(),
                obrigacao == null ? null : limite(obrigacao),
                n.getDtEmissao(),
                !n.naoLida());
    }

    private static LocalDate limite(br.com.clyvovet.server.obrigacao.Obrigacao obrigacao) {
        return obrigacao.getDtJanelaFim() != null
                ? obrigacao.getDtJanelaFim()
                : obrigacao.getDtPrevista();
    }

    /** Prazo ja vencido: a tela marca, e e o que faz o tutor abrir. */
    public boolean vencida() {
        return dataLimite != null && dataLimite.isBefore(LocalDate.now());
    }
}
