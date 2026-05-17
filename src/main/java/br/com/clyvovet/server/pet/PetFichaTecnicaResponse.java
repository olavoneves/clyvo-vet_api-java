package br.com.clyvovet.server.pet;

import br.com.clyvovet.server.enums.PetPorte;
import br.com.clyvovet.server.enums.PetSexo;
import br.com.clyvovet.server.enums.PetStatus;

import java.time.LocalDate;

public record PetFichaTecnicaResponse(
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
        String tutorEmail,
        String tutorTelefone,
        Long racaId,
        String racaNome,
        Long especieId,
        String especieNome
) {
    public static PetFichaTecnicaResponse from(Pet p) {
        return new PetFichaTecnicaResponse(
                p.getId(), p.getNome(), p.getDtNascimento(), p.getSexo(),
                p.getMicrochip(), p.getRga(), p.getPelagem(), p.getPorte(),
                p.getCastrado(), p.getStatus(), p.getObservacaoGeral(),
                p.getTutor().getId(), p.getTutor().getNome(), p.getTutor().getEmail(), p.getTutor().getTelefone(),
                p.getRaca().getId(), p.getRaca().getNome(),
                p.getRaca().getEspecie().getId(), p.getRaca().getEspecie().getNome());
    }
}
