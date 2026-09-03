package br.com.clyvovet.server.obrigacao;

import java.time.LocalDateTime;
import java.util.List;

/** Obrigacao com a linha do tempo que a procedure registrou a cada transicao. */
public record ObrigacaoDetalheResponse(
        ObrigacaoResponse obrigacao,
        String correlationId,
        LocalDateTime dtNotificada,
        LocalDateTime dtRespondida,
        LocalDateTime dtAgendada,
        LocalDateTime dtCumprida,
        List<ObrigacaoTransicaoResponse> transicoes
) {
    public static ObrigacaoDetalheResponse from(Obrigacao o, List<ObrigacaoTransicao> transicoes) {
        return new ObrigacaoDetalheResponse(
                ObrigacaoResponse.from(o),
                o.getDsCorrelationId(),
                o.getDtNotificada(), o.getDtRespondida(), o.getDtAgendada(), o.getDtCumprida(),
                transicoes.stream().map(ObrigacaoTransicaoResponse::from).toList());
    }
}
