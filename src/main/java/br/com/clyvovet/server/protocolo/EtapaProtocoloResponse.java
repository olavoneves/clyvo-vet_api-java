package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.TipoIntervalo;

import java.math.BigDecimal;

public record EtapaProtocoloResponse(
        Long id,
        Integer ordem,
        String nome,
        TipoIntervalo tipoIntervalo,
        Integer offsetDias,
        Integer janelaAntes,
        Integer janelaDepois,
        Integer recorrenciaDias,
        String procedimento,
        BigDecimal valorReferencia,
        Integer maxOcorrencias
) {
    public static EtapaProtocoloResponse from(EtapaProtocolo e) {
        return new EtapaProtocoloResponse(
                e.getId(), e.getNrOrdem(), e.getNmEtapa(), e.getDsTipoIntervalo(),
                e.getNrOffsetDias(), e.getNrJanelaAntes(), e.getNrJanelaDepois(),
                e.getNrRecorrenciaDias(), e.getDsProcedimento(),
                e.getNrValorReferencia(), e.getNrMaxOcorrencias());
    }
}
