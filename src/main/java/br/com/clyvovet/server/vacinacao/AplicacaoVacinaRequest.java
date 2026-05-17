package br.com.clyvovet.server.vacinacao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AplicacaoVacinaRequest(
        @NotNull Long petId,
        @NotNull Long tipoVacinaId,
        @NotNull Long veterinarioId,
        Long consultaId,
        @NotNull LocalDate dtAplicacao,
        Integer numeroDose,
        @Size(max = 30) String lote
) {}
