package br.com.clyvovet.server.tipoalergia;

import br.com.clyvovet.server.enums.CategoriaAlergia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_TIPO_ALERGIA")
@Getter
@Setter
public class TipoAlergia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_alergia")
    private Long id;

    @Column(name = "nm_alergia", nullable = false, unique = true, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_categoria", length = 20)
    private CategoriaAlergia categoria;
}
