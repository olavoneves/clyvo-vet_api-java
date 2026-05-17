package br.com.clyvovet.server.fichaclinica.condicao;

import java.time.LocalDate;

public record CondicaoPetResponse(
        Long id, Long petId, String petNome, Long tipoCondicaoId, String tipoCondicaoNome,
        LocalDate dtDiagnostico, String observacao, Boolean ativo, Long consultaOrigemId
) {
    public static CondicaoPetResponse from(CondicaoPet c) {
        return new CondicaoPetResponse(
                c.getId(), c.getPet().getId(), c.getPet().getNome(),
                c.getTipoCondicao().getId(), c.getTipoCondicao().getNome(),
                c.getDtDiagnostico(), c.getObservacao(), c.getAtivo(),
                c.getConsultaOrigem() != null ? c.getConsultaOrigem().getId() : null);
    }
}
