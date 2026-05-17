package br.com.clyvovet.server.iot.sensor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SensorIotRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull Long tipoSensorId,
        @NotNull Long petId,
        @NotNull LocalDate dtInstalacao,
        LocalDate dtUltimaCalibracao,
        Boolean ativo
) {}
