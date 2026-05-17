package br.com.clyvovet.server.especie;

public record EspecieResponse(Long id, String nome, String descricao) {

    public static EspecieResponse from(Especie e) {
        return new EspecieResponse(e.getId(), e.getNome(), e.getDescricao());
    }
}
