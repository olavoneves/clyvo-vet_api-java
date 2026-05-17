package br.com.clyvovet.server.fichaclinica.condicao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CondicaoPetRequest(
        @NotNull Long petId,
        @NotNull Long tipoCondicaoId,
        @NotNull LocalDate dtDiagnostico,
        @Size(max = 500) String observacao,
        Boolean ativo,
        Long consultaOrigemId
) {}
