package br.com.clyvovet.server.tiposensor;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_TIPO_SENSOR")
@Getter
@Setter
public class TipoSensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_sensor")
    private Long id;

    @Column(name = "nm_tipo", nullable = false, unique = true, length = 60)
    private String nome;

    @Column(name = "ds_unidade", nullable = false, length = 20)
    private String unidade;

    @Column(name = "nr_valor_min")
    private Double valorMin;

    @Column(name = "nr_valor_max")
    private Double valorMax;
}
