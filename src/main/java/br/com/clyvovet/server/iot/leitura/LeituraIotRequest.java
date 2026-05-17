package br.com.clyvovet.server.iot.leitura;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LeituraIotRequest(
        @NotNull Long sensorId,
        @NotNull LocalDateTime dtLeitura,
        @NotNull BigDecimal valor
) {}
