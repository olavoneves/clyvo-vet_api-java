package br.com.clyvovet.server.consulta;

import br.com.clyvovet.server.enums.ConsultaStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConsultaRequest(
        @NotNull LocalDate dtConsulta,
        @Size(max = 500) String motivo,
        @Size(max = 1000) String diagnostico,
        ConsultaStatus status,
        // nulo enquanto a consulta não fecha, e em atendimento de cortesia
        @PositiveOrZero BigDecimal nrValor,
        @NotNull Long petId,
        @NotNull Long veterinarioId
) {}
