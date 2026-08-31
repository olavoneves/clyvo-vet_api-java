package br.com.clyvovet.server.tutor;

import br.com.clyvovet.server.enums.CanalPreferencial;

import java.time.LocalDate;

public record TutorResponse(
        Long id,
        String nome,
        String email,
        String telefone,
        String telefoneEmergencia,
        CanalPreferencial canalPreferencial,
        LocalDate dtCadastro,
        Long clinicaId
) {
    public static TutorResponse from(Tutor t) {
        return new TutorResponse(
                t.getId(), t.getNome(), t.getEmail(), t.getTelefone(),
                t.getTelefoneEmergencia(), t.getCanalPreferencial(), t.getDtCadastro(),
                t.getClinica().getId());
    }
}
