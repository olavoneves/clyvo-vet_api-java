package br.com.clyvovet.server.consulta;

import br.com.clyvovet.server.enums.ConsultaStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ConsultaRequest(
        @NotNull LocalDate dtConsulta,
        @Size(max = 500) String motivo,
        @Size(max = 1000) String diagnostico,
        ConsultaStatus status,
        @NotNull Long petId,
        @NotNull Long veterinarioId
) {}
