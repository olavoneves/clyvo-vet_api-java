package br.com.clyvovet.server.exame;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ExameRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull LocalDate dtRealizacao,
        @Size(max = 2000) String resultado,
        @NotNull Long consultaId,
        Long vetSolicitanteId
) {}
