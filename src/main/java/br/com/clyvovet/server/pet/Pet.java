package br.com.clyvovet.server.pet;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.converter.SimNaoConverter;
import br.com.clyvovet.server.enums.PetPorte;
import br.com.clyvovet.server.enums.PetSexo;
import br.com.clyvovet.server.enums.PetStatus;
import br.com.clyvovet.server.raca.Raca;
import br.com.clyvovet.server.tenant.TenantFilters;
import br.com.clyvovet.server.tutor.Tutor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

@Entity
@Table(name = "TB_CLV_PET")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO)
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pet")
    private Long id;

    @Column(name = "nm_pet", nullable = false, length = 100)
    private String nome;

    @Column(name = "dt_nascimento")
    private LocalDate dtNascimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_sexo", length = 1)
    private PetSexo sexo;

    @Column(name = "nr_microchip", unique = true, length = 30)
    private String microchip;

    @Column(name = "nr_rga", length = 30)
    private String rga;

    @Column(name = "ds_pelagem", length = 80)
    private String pelagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_porte", length = 10)
    private PetPorte porte;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "fl_castrado", columnDefinition = "CHAR(1)")
    private Boolean castrado;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status_pet", length = 15)
    private PetStatus status;

    @Column(name = "ds_observacao_geral", length = 2000)
    private String observacaoGeral;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tutor", nullable = false)
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_raca", nullable = false)
    private Raca raca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clinica", nullable = false)
    private Clinica clinica;
}
