package br.com.clyvovet.server.pet;

import br.com.clyvovet.server.enums.PetPorte;
import br.com.clyvovet.server.enums.PetSexo;
import br.com.clyvovet.server.enums.PetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PetRequest(
        @NotBlank @Size(max = 100) String nome,
        LocalDate dtNascimento,
        PetSexo sexo,
        @Size(max = 30) String microchip,
        @Size(max = 30) String rga,
        @Size(max = 80) String pelagem,
        PetPorte porte,
        Boolean castrado,
        PetStatus status,
        @Size(max = 2000) String observacaoGeral,
        @NotNull Long tutorId,
        @NotNull Long racaId
) {}
