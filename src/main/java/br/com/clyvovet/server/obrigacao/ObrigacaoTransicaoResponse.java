package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.enums.ObrigacaoStatus;

import java.time.LocalDateTime;

public record ObrigacaoTransicaoResponse(
        Long id,
        ObrigacaoStatus de,
        ObrigacaoStatus para,
        String motivo,
        String usuario,
        LocalDateTime dtOcorrencia
) {
    public static ObrigacaoTransicaoResponse from(ObrigacaoTransicao t) {
        return new ObrigacaoTransicaoResponse(
                t.getId(), t.getDsStatusDe(), t.getDsStatusPara(),
                t.getDsMotivo(), t.getNmUsuario(), t.getDtOcorrencia());
    }
}
