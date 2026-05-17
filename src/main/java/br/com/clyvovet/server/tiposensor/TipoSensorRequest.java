package br.com.clyvovet.server.tiposensor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoSensorRequest(
        @NotBlank @Size(max = 60) String nome,
        @NotBlank @Size(max = 20) String unidade,
        Double valorMin,
        Double valorMax
) {}
