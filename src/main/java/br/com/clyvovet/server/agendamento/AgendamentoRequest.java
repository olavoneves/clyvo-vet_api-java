package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
import br.com.clyvovet.server.enums.CanalPreferencial;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AgendamentoRequest(
        @NotNull LocalDate dtAgendamento,
        @NotBlank @Pattern(regexp = "\\d{2}:\\d{2}", message = "Formato HH:mm") String hrAgendamento,
        AgendamentoStatus status,
        CanalPreferencial canalOrigem,
        @Size(max = 300) String motivoCancelamento,
        @NotNull Long petId,
        @NotNull Long veterinarioId,
        Long consultaId
) {}
