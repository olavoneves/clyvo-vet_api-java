package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.enums.ObrigacaoStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ObrigacaoResponse(
        Long id,
        Long petId,
        String petNome,
        Long etapaId,
        String etapaNome,
        String protocoloCodigo,
        String protocoloNome,
        ObrigacaoStatus status,
        LocalDate dtPrevista,
        LocalDate dtJanelaInicio,
        LocalDate dtJanelaFim,
        Boolean grupoControle,
        BigDecimal valorEstimado,
        BigDecimal valorRealizado
) {
    public static ObrigacaoResponse from(Obrigacao o) {
        var etapa = o.getEtapa();
        var protocolo = etapa.getVersaoProtocolo().getProtocolo();
        return new ObrigacaoResponse(
                o.getId(),
                o.getPet().getId(), o.getPet().getNome(),
                etapa.getId(), etapa.getNmEtapa(),
                protocolo.getDsCodigo(), protocolo.getNmProtocolo(),
                o.getDsStatus(), o.getDtPrevista(), o.getDtJanelaInicio(), o.getDtJanelaFim(),
                o.getFlGrupoControle(), o.getNrValorEstimado(), o.getNrValorRealizado());
    }
}
