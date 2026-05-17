package br.com.clyvovet.server.iot.sensor;

import br.com.clyvovet.server.converter.SimNaoConverter;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.tiposensor.TipoSensor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "TB_CLV_SENSOR_IOT")
@Getter
@Setter
public class SensorIot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sensor")
    private Long id;

    @Column(name = "nm_sensor", nullable = false, length = 100)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_sensor", nullable = false)
    private TipoSensor tipoSensor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pet", nullable = false)
    private Pet pet;

    @Column(name = "dt_instalacao", nullable = false)
    private LocalDate dtInstalacao;

    @Column(name = "dt_ultima_calibracao")
    private LocalDate dtUltimaGalibracao;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "fl_ativo", length = 1)
    private Boolean ativo;
}
