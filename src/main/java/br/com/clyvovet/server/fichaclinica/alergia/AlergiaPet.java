package br.com.clyvovet.server.fichaclinica.alergia;

import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.enums.SeveridadeAlergia;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.tipoalergia.TipoAlergia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "TB_CLV_ALERGIA_PET")
@Getter
@Setter
public class AlergiaPet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alergia_pet")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pet", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_alergia", nullable = false)
    private TipoAlergia tipoAlergia;

    @Column(name = "ds_reacao", length = 300)
    private String reacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_severidade", length = 10)
    private SeveridadeAlergia severidade;

    @Column(name = "dt_identificacao", nullable = false)
    private LocalDate dtIdentificacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta_origem")
    private Consulta consultaOrigem;
}
