package br.com.clyvovet.server.prescricao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrescricaoRequest(
        @NotNull Long consultaId,
        @NotNull Long medicamentoId,
        @NotBlank @Size(max = 100) String dosagem,
        @Size(max = 100) String frequencia,
        Integer duracaoDias
) {}
