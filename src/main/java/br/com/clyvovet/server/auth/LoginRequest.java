package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.enums.TipoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String senha,
        @NotNull TipoUsuario tipo
) {}
