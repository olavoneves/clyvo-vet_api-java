package br.com.clyvovet.server.medicamento;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_MEDICAMENTO")
@Getter
@Setter
public class Medicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_medicamento")
    private Long id;

    @Column(name = "nm_medicamento", nullable = false, unique = true, length = 150)
    private String nome;

    @Column(name = "ds_principio_ativo", length = 200)
    private String principioAtivo;

    @Column(name = "ds_classe_terapeutica", length = 100)
    private String classeTerapeutica;
}
