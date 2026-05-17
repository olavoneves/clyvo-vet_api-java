package br.com.clyvovet.server.veterinario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VeterinarioRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 20) String crmv,
        @Size(max = 100) String especialidade,
        @NotNull Long clinicaId,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 6, max = 100) String senha
) {}
