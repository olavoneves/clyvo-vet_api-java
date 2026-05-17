package br.com.clyvovet.server.tipocondicao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoCondicaoRequest(
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 60) String categoria
) {}
