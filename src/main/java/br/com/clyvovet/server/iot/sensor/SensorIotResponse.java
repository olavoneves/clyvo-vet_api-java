package br.com.clyvovet.server.iot.sensor;

import java.time.LocalDate;

public record SensorIotResponse(
        Long id, String nome, Long tipoSensorId, String tipoSensorNome,
        Long petId, String petNome, LocalDate dtInstalacao, LocalDate dtUltimaCalibracao, Boolean ativo
) {
    public static SensorIotResponse from(SensorIot s) {
        return new SensorIotResponse(
                s.getId(), s.getNome(), s.getTipoSensor().getId(), s.getTipoSensor().getNome(),
                s.getPet().getId(), s.getPet().getNome(),
                s.getDtInstalacao(), s.getDtUltimaGalibracao(), s.getAtivo());
    }
}
