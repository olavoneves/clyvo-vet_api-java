package br.com.clyvovet.server.iot.alerta;

import br.com.clyvovet.server.enums.SeveridadeAlerta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AlertaIotRequest(
        @NotNull Long leituraId,
        @NotBlank @Size(max = 500) String descricao,
        SeveridadeAlerta severidade,
        @NotNull LocalDateTime dtAlerta,
        Boolean resolvido,
        Long consultaId
) {}
