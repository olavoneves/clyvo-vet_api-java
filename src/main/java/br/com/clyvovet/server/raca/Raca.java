package br.com.clyvovet.server.raca;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especie", nullable = false)
    private Especie especie;
}
