package br.com.clyvovet.server.enums;

/** Maquina de estados de TB_CLV_OBRIGACAO, governada por PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO. */
public enum ObrigacaoStatus {
    PREVISTA, NOTIFICADA, RESPONDIDA, AGENDADA, CUMPRIDA, PERDIDA, CANCELADA
}
