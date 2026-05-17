package br.com.clyvovet.server.exame;

import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.veterinario.Veterinario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "TB_CLV_EXAME")
@Getter
@Setter
public class Exame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_exame")
    private Long id;

    @Column(name = "nm_exame", nullable = false, length = 100)
    private String nome;

    @Column(name = "dt_realizacao", nullable = false)
    private LocalDate dtRealizacao;

    @Column(name = "ds_resultado", length = 2000)
    private String resultado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = false)
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vet_solicitante")
    private Veterinario vetSolicitante;
}
