package br.com.clyvovet.server.tipoalergia;

import br.com.clyvovet.server.enums.CategoriaAlergia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoAlergiaRequest(
        @NotBlank @Size(max = 120) String nome,
        CategoriaAlergia categoria
) {}
