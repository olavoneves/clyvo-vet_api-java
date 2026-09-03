package br.com.clyvovet.server.veterinario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sem clinicaId: POST /veterinarios exige colaborador autenticado, e a clinica
 * vem do TenantContext. Aceitar o id no corpo permitiria cadastrar veterinario
 * na clinica alheia.
 */
public record VeterinarioRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 20) String crmv,
        @Size(max = 100) String especialidade,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 6, max = 100) String senha
) {}
