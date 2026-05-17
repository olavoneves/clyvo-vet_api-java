package br.com.clyvovet.server.tipovacina;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_TIPO_VACINA")
@Getter
@Setter
public class TipoVacina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_vacina")
    private Long id;

    @Column(name = "nm_vacina", nullable = false, unique = true, length = 100)
    private String nome;

    @Column(name = "ds_fabricante", length = 100)
    private String fabricante;

    @Column(name = "nr_dose_intervalo_dias")
    private Integer doseIntervaloDias;
}
