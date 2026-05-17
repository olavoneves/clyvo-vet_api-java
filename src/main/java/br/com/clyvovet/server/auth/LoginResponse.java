package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.enums.TipoUsuario;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        TipoUsuario tipo,
        Long id,
        String nome,
        String email
) {}
