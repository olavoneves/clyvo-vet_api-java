package br.com.clyvovet.server.veterinario;

public record VeterinarioResponse(Long id, String nome, String crmv, String especialidade, Long clinicaId, String clinicaNome) {

    public static VeterinarioResponse from(Veterinario v) {
        return new VeterinarioResponse(
                v.getId(), v.getNome(), v.getCrmv(), v.getEspecialidade(),
                v.getClinica().getId(), v.getClinica().getNome());
    }
}
