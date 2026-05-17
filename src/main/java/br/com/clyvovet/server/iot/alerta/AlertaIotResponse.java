package br.com.clyvovet.server.iot.alerta;

import br.com.clyvovet.server.enums.SeveridadeAlerta;

import java.time.LocalDateTime;

public record AlertaIotResponse(
        Long id, Long leituraId, String descricao, SeveridadeAlerta severidade,
        LocalDateTime dtAlerta, Boolean resolvido, Long consultaId
) {
    public static AlertaIotResponse from(AlertaIot a) {
        return new AlertaIotResponse(
                a.getId(), a.getLeitura().getId(), a.getDescricao(), a.getSeveridade(),
                a.getDtAlerta(), a.getResolvido(),
                a.getConsulta() != null ? a.getConsulta().getId() : null);
    }
}
