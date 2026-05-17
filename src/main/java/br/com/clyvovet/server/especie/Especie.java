package br.com.clyvovet.server.especie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_ESPECIE")
@Getter
@Setter
public class Especie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_especie")
    private Long id;

    @Column(name = "nm_especie", nullable = false, unique = true, length = 60)
    private String nome;

    @Column(name = "ds_especie", length = 300)
    private String descricao;
}
