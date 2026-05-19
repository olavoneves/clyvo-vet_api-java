package br.com.clyvovet.server.colaborador;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_COLABORADOR")
@Getter
@Setter
public class Colaborador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_colaborador")
    private Long id;

    @Column(name = "nm_colaborador", nullable = false, length = 100)
    private String nome;

    @Column(name = "ds_email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "ds_senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Column(name = "ds_cargo", length = 100)
    private String cargo;
}
