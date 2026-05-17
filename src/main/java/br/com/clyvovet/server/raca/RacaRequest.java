package br.com.clyvovet.server.raca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RacaRequest(
        @NotBlank @Size(max = 80) String nome,
        @NotNull Long especieId
) {}
