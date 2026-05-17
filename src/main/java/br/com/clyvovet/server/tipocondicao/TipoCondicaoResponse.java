package br.com.clyvovet.server.tipocondicao;

public record TipoCondicaoResponse(Long id, String nome, String categoria) {

    public static TipoCondicaoResponse from(TipoCondicao t) {
        return new TipoCondicaoResponse(t.getId(), t.getNome(), t.getCategoria());
    }
}
