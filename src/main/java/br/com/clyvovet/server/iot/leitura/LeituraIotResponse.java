package br.com.clyvovet.server.iot.leitura;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LeituraIotResponse(Long id, Long sensorId, String sensorNome, LocalDateTime dtLeitura, BigDecimal valor) {

    public static LeituraIotResponse from(LeituraIot l) {
        return new LeituraIotResponse(
                l.getId(), l.getSensor().getId(), l.getSensor().getNome(), l.getDtLeitura(), l.getValor());
    }
}
