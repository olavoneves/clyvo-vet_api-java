package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.VersaoProtocoloStatus;

import java.time.LocalDate;
import java.util.List;

public record VersaoProtocoloResponse(
        Long id,
        Integer versao,
        VersaoProtocoloStatus status,
        LocalDate vigenteDe,
        LocalDate vigenteAte,
        String fonteClinica,
        String notas,
        List<EtapaProtocoloResponse> etapas
) {
    public static VersaoProtocoloResponse from(VersaoProtocolo v, List<EtapaProtocolo> etapas) {
        return new VersaoProtocoloResponse(
                v.getId(), v.getNrVersao(), v.getDsStatus(), v.getDtVigenteDe(),
                v.getDtVigenteAte(), v.getDsFonteClinica(), v.getDsNotas(),
                etapas.stream().map(EtapaProtocoloResponse::from).toList());
    }
}
