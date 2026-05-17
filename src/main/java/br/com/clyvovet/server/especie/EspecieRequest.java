package br.com.clyvovet.server.especie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EspecieRequest(
        @NotBlank @Size(max = 60) String nome,
        @Size(max = 300) String descricao
) {}
