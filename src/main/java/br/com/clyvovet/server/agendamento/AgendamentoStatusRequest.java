package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgendamentoStatusRequest(
        @NotNull AgendamentoStatus status,
        @Size(max = 300) String motivoCancelamento
) {}
