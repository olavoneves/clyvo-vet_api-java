package br.com.clyvovet.server.medicamento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MedicamentoRequest(
        @NotBlank @Size(max = 150) String nome,
        @Size(max = 200) String principioAtivo,
        @Size(max = 100) String classeTerapeutica
) {}
