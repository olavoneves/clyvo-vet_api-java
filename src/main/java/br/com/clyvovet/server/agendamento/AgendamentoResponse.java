package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
import br.com.clyvovet.server.enums.CanalPreferencial;

import java.time.LocalDate;

public record AgendamentoResponse(
        Long id,
        LocalDate dtAgendamento,
        String hrAgendamento,
        AgendamentoStatus status,
        CanalPreferencial canalOrigem,
        String motivoCancelamento,
        Long petId,
        String petNome,
        Long veterinarioId,
        String veterinarioNome,
        Long consultaId
) {
    public static AgendamentoResponse from(Agendamento a) {
        return new AgendamentoResponse(
                a.getId(), a.getDtAgendamento(), a.getHrAgendamento(), a.getStatus(), a.getCanalOrigem(),
                a.getMotivoCancelamento(),
                a.getPet().getId(), a.getPet().getNome(),
                a.getVeterinario().getId(), a.getVeterinario().getNome(),
                a.getIdConsulta());
    }
}
