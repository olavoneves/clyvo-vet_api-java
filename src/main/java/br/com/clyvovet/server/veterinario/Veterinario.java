package br.com.clyvovet.server.veterinario;

import br.com.clyvovet.server.clinica.Clinica;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_VETERINARIO")
@Getter
@Setter
public class Veterinario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_veterinario")
    private Long id;

    @Column(name = "nm_veterinario", nullable = false, length = 100)
    private String nome;

    @Column(name = "nr_crmv", nullable = false, unique = true, length = 20)
    private String crmv;

    @Column(name = "ds_especialidade", length = 100)
    private String especialidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clinica", nullable = false)
    private Clinica clinica;
}
