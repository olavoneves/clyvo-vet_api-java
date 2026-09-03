package br.com.clyvovet.server.raca;

import br.com.clyvovet.server.enums.PetPorte;

public record RacaResponse(
        Long id,
        String nome,
        String dsGrupoRaca,
        PetPorte dsPortePadrao,
        Boolean flBraquicefalico,
        String dsPredisposicoes,
        Long especieId,
        String especieNome
) {
    public static RacaResponse from(Raca r) {
        return new RacaResponse(
                r.getId(), r.getNome(), r.getDsGrupoRaca(), r.getDsPortePadrao(),
                r.getFlBraquicefalico(), r.getDsPredisposicoes(),
                r.getEspecie().getId(), r.getEspecie().getNome());
    }
}
