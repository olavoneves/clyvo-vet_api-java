package br.com.clyvovet.server.consulta;

import br.com.clyvovet.server.enums.ConsultaStatus;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.veterinario.Veterinario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "TB_CLV_CONSULTA")
@Getter
@Setter
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consulta")
    private Long id;

    @Column(name = "dt_consulta", nullable = false)
    private LocalDate dtConsulta;

    @Column(name = "ds_motivo", length = 500)
    private String motivo;

    @Column(name = "ds_diagnostico", length = 1000)
    private String diagnostico;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status", length = 20)
    private ConsultaStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pet", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_veterinario", nullable = false)
    private Veterinario veterinario;
}
