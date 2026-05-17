package br.com.clyvovet.server.tipoalergia;

import br.com.clyvovet.server.enums.CategoriaAlergia;

public record TipoAlergiaResponse(Long id, String nome, CategoriaAlergia categoria) {

    public static TipoAlergiaResponse from(TipoAlergia t) {
        return new TipoAlergiaResponse(t.getId(), t.getNome(), t.getCategoria());
    }
}
