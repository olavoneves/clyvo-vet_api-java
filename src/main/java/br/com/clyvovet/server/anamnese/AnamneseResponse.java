package br.com.clyvovet.server.anamnese;

import br.com.clyvovet.server.enums.CondicaoCorporal;

import java.math.BigDecimal;

public record AnamneseResponse(
        Long id,
        Long consultaId,
        String queixaPrincipal,
        String historicoRelatado,
        BigDecimal pesoKg,
        BigDecimal temperaturaC,
        Integer freqCardiaca,
        Integer freqRespiratoria,
        CondicaoCorporal condicaoCorporal,
        String mucosas,
        String linfonodos,
        String observacoesClinicas
) {
    public static AnamneseResponse from(Anamnese a) {
        return new AnamneseResponse(
                a.getId(), a.getConsulta().getId(), a.getQueixaPrincipal(), a.getHistoricoRelatado(),
                a.getPesoKg(), a.getTemperaturaC(), a.getFreqCardiaca(), a.getFreqRespiratoria(),
                a.getCondicaoCorporal(), a.getMucosas(), a.getLinfonodos(), a.getObservacoesClinicas());
    }
}
