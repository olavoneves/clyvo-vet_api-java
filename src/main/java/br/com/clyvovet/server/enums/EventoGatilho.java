package br.com.clyvovet.server.enums;

/** Evento que o motor avalia para decidir se um protocolo se aplica ao pet. */
public enum EventoGatilho {
    CONSULTA_REGISTRADA, CONDICAO_DIAGNOSTICADA, FASE_VIDA_ALTERADA, PESO_ATUALIZADO,
    VACINA_APLICADA, PROCEDIMENTO_REALIZADO, PET_CADASTRADO
}
