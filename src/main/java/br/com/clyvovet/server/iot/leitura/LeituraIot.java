package br.com.clyvovet.server.iot.leitura;

import br.com.clyvovet.server.iot.sensor.SensorIot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TB_CLV_LEITURA_IOT")
@Getter
@Setter
public class LeituraIot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_leitura")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sensor", nullable = false)
    private SensorIot sensor;

    @Column(name = "dt_leitura", nullable = false)
    private LocalDateTime dtLeitura;

    @Column(name = "nr_valor", nullable = false, precision = 10, scale = 4)
    private BigDecimal valor;
}
