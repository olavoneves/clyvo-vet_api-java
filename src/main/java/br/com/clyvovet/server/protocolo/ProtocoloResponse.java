package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.ProtocoloCategoria;
import br.com.clyvovet.server.enums.ProtocoloEspecie;

public record ProtocoloResponse(
        Long id,
        String codigo,
        String nome,
        ProtocoloEspecie especie,
        ProtocoloCategoria categoria,
        String descricao,
        Boolean ativo,
        /** Nulo indica protocolo global, mantido pela plataforma. */
        Long clinicaId
) {
    public static ProtocoloResponse from(Protocolo p) {
        return new ProtocoloResponse(
                p.getId(), p.getDsCodigo(), p.getNmProtocolo(), p.getDsEspecie(),
                p.getDsCategoria(), p.getDsDescricao(), p.getFlAtivo(),
                p.getClinica() != null ? p.getClinica().getId() : null);
    }
}
