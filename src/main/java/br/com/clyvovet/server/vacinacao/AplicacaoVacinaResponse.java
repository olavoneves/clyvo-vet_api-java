package br.com.clyvovet.server.vacinacao;

import java.time.LocalDate;

public record AplicacaoVacinaResponse(
        Long id,
        Long petId,
        String petNome,
        Long tipoVacinaId,
        String tipoVacinaNome,
        Long veterinarioId,
        String veterinarioNome,
        Long consultaId,
        LocalDate dtAplicacao,
        Integer numeroDose,
        String lote,
        LocalDate proximoReforco
) {
    public static AplicacaoVacinaResponse from(AplicacaoVacina a) {
        LocalDate proximo = null;
        if (a.getTipoVacina().getDoseIntervaloDias() != null) {
            proximo = a.getDtAplicacao().plusDays(a.getTipoVacina().getDoseIntervaloDias());
        }
        return new AplicacaoVacinaResponse(
                a.getId(), a.getPet().getId(), a.getPet().getNome(),
                a.getTipoVacina().getId(), a.getTipoVacina().getNome(),
                a.getVeterinario().getId(), a.getVeterinario().getNome(),
                a.getConsulta() != null ? a.getConsulta().getId() : null,
                a.getDtAplicacao(), a.getNumeroDose(), a.getLote(), proximo);
    }
}
