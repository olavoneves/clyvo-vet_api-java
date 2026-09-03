package br.com.clyvovet.server.raca;

import br.com.clyvovet.server.converter.SimNaoConverter;
import br.com.clyvovet.server.enums.PetPorte;
import br.com.clyvovet.server.especie.Especie;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_RACA")
@Getter
@Setter
public class Raca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_raca")
    private Long id;

    @Column(name = "nm_raca", nullable = false, length = 80)
    private String nome;

    /** Agrupamento clinico da raca: Toy, Pastor, Molossoide, etc. */
    @Column(name = "ds_grupo_raca", length = 60)
    private String dsGrupoRaca;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_porte_padrao", length = 10)
    private PetPorte dsPortePadrao;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "fl_braquicefalico", columnDefinition = "CHAR(1)")
    private Boolean flBraquicefalico;

    /** Predisposicoes conhecidas da raca, insumo do motor de protocolo em PL/SQL. */
    @Column(name = "ds_predisposicoes", length = 1000)
    private String dsPredisposicoes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especie", nullable = false)
    private Especie especie;
}
