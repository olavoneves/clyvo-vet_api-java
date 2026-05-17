package br.com.clyvovet.server.fichaclinica.alergia;

import br.com.clyvovet.server.enums.SeveridadeAlergia;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AlergiaPetRequest(
        @NotNull Long petId,
        @NotNull Long tipoAlergiaId,
        @Size(max = 300) String reacao,
        SeveridadeAlergia severidade,
        @NotNull LocalDate dtIdentificacao,
        Long consultaOrigemId
) {}
