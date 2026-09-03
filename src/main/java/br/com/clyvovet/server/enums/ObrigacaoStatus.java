package br.com.clyvovet.server.enums;

/** Maquina de estados de TB_CLV_OBRIGACAO, governada por PR_CLV_TRANSITAR_OBRIGACAO. */
public enum ObrigacaoStatus {
    PREVISTA, NOTIFICADA, RESPONDIDA, AGENDADA, CUMPRIDA, PERDIDA, CANCELADA
}
