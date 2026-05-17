package br.com.clyvovet.server.auditoria;

import br.com.clyvovet.server.enums.AmbienteLog;

import java.time.LocalDateTime;

public record LogErroResponse(
        Long id, String procedure, String usuario, LocalDateTime dtOcorrencia,
        Long codigoErro, String mensagemErro, AmbienteLog ambiente
) {
    public static LogErroResponse from(LogErro l) {
        return new LogErroResponse(
                l.getId(), l.getProcedure(), l.getUsuario(), l.getDtOcorrencia(),
                l.getCodigoErro(), l.getMensagemErro(), l.getAmbiente());
    }
}
