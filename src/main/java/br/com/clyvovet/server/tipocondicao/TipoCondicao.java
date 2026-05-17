package br.com.clyvovet.server.tipocondicao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_TIPO_CONDICAO")
@Getter
@Setter
public class TipoCondicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_condicao")
    private Long id;

    @Column(name = "nm_condicao", nullable = false, unique = true, length = 120)
    private String nome;

    @Column(name = "ds_categoria", length = 60)
    private String categoria;
}
