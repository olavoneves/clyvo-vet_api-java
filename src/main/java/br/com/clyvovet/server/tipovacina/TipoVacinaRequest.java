package br.com.clyvovet.server.tipovacina;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoVacinaRequest(
        @NotBlank @Size(max = 100) String nome,
        @Size(max = 100) String fabricante,
        Integer doseIntervaloDias
) {}
