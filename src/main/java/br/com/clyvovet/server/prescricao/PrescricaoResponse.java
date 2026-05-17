package br.com.clyvovet.server.prescricao;

public record PrescricaoResponse(
        Long id,
        Long consultaId,
        Long medicamentoId,
        String medicamentoNome,
        String dosagem,
        String frequencia,
        Integer duracaoDias
) {
    public static PrescricaoResponse from(Prescricao p) {
        return new PrescricaoResponse(
                p.getId(), p.getConsulta().getId(),
                p.getMedicamento().getId(), p.getMedicamento().getNome(),
                p.getDosagem(), p.getFrequencia(), p.getDuracaoDias());
    }
}
