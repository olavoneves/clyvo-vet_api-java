package br.com.clyvovet.server.fichaclinica.alergia;

import br.com.clyvovet.server.enums.SeveridadeAlergia;

import java.time.LocalDate;

public record AlergiaPetResponse(
        Long id, Long petId, String petNome, Long tipoAlergiaId, String tipoAlergiaNome,
        String reacao, SeveridadeAlergia severidade, LocalDate dtIdentificacao, Long consultaOrigemId
) {
    public static AlergiaPetResponse from(AlergiaPet a) {
        return new AlergiaPetResponse(
                a.getId(), a.getPet().getId(), a.getPet().getNome(),
                a.getTipoAlergia().getId(), a.getTipoAlergia().getNome(),
                a.getReacao(), a.getSeveridade(), a.getDtIdentificacao(),
                a.getConsultaOrigem() != null ? a.getConsultaOrigem().getId() : null);
    }
}
