package br.com.clyvovet.server.raca;

public record RacaResponse(Long id, String nome, Long especieId, String especieNome) {

    public static RacaResponse from(Raca r) {
        return new RacaResponse(r.getId(), r.getNome(), r.getEspecie().getId(), r.getEspecie().getNome());
    }
}
