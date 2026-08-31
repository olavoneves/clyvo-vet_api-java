package br.com.clyvovet.server.raca;

import br.com.clyvovet.server.enums.PetPorte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RacaRequest(
        @NotBlank @Size(max = 80) String nome,
        @Size(max = 60) String dsGrupoRaca,
        PetPorte dsPortePadrao,
        Boolean flBraquicefalico,
        @Size(max = 1000) String dsPredisposicoes,
        @NotNull Long especieId
) {}
