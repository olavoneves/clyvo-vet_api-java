package br.com.clyvovet.server.pet;

import br.com.clyvovet.server.enums.PetPorte;
import br.com.clyvovet.server.enums.PetSexo;
import br.com.clyvovet.server.enums.PetStatus;

import java.time.LocalDate;

public record PetResponse(
        Long id,
        String nome,
        LocalDate dtNascimento,
        PetSexo sexo,
        String microchip,
        String rga,
        String pelagem,
        PetPorte porte,
        Boolean castrado,
        PetStatus status,
        String observacaoGeral,
        Long tutorId,
        String tutorNome,
        Long racaId,
        String racaNome
) {
    public static PetResponse from(Pet p) {
        return new PetResponse(
                p.getId(), p.getNome(), p.getDtNascimento(), p.getSexo(),
                p.getMicrochip(), p.getRga(), p.getPelagem(), p.getPorte(),
                p.getCastrado(), p.getStatus(), p.getObservacaoGeral(),
                p.getTutor().getId(), p.getTutor().getNome(),
                p.getRaca().getId(), p.getRaca().getNome());
    }
}
