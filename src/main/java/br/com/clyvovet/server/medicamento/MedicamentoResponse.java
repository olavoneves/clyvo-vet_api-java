package br.com.clyvovet.server.medicamento;

public record MedicamentoResponse(Long id, String nome, String principioAtivo, String classeTerapeutica) {

    public static MedicamentoResponse from(Medicamento m) {
        return new MedicamentoResponse(m.getId(), m.getNome(), m.getPrincipioAtivo(), m.getClasseTerapeutica());
    }
}
