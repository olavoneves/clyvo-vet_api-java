package br.com.clyvovet.server.tipovacina;

public record TipoVacinaResponse(Long id, String nome, String fabricante, Integer doseIntervaloDias) {

    public static TipoVacinaResponse from(TipoVacina t) {
        return new TipoVacinaResponse(t.getId(), t.getNome(), t.getFabricante(), t.getDoseIntervaloDias());
    }
}
