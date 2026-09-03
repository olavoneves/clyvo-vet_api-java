package br.com.clyvovet.server.consulta;

import br.com.clyvovet.server.enums.ConsultaStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConsultaResponse(
        Long id,
        LocalDate dtConsulta,
        String motivo,
        String diagnostico,
        ConsultaStatus status,
        BigDecimal nrValor,
        Long petId,
        String petNome,
        Long veterinarioId,
        String veterinarioNome
) {
    public static ConsultaResponse from(Consulta c) {
        return new ConsultaResponse(
                c.getId(), c.getDtConsulta(), c.getMotivo(), c.getDiagnostico(), c.getStatus(),
                c.getNrValor(),
                c.getPet().getId(), c.getPet().getNome(),
                c.getVeterinario().getId(), c.getVeterinario().getNome());
    }
}
