package br.com.clyvovet.server.exame;

import java.time.LocalDate;

public record ExameResponse(
        Long id, String nome, LocalDate dtRealizacao, String resultado,
        Long consultaId, Long vetSolicitanteId, String vetSolicitanteNome
) {
    public static ExameResponse from(Exame e) {
        return new ExameResponse(
                e.getId(), e.getNome(), e.getDtRealizacao(), e.getResultado(),
                e.getConsulta().getId(),
                e.getVetSolicitante() != null ? e.getVetSolicitante().getId() : null,
                e.getVetSolicitante() != null ? e.getVetSolicitante().getNome() : null);
    }
}
