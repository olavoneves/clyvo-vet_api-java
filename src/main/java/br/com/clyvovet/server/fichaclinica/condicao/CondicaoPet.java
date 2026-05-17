package br.com.clyvovet.server.fichaclinica.condicao;

import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.converter.SimNaoConverter;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.tipocondicao.TipoCondicao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "TB_CLV_CONDICAO_PET")
@Getter
@Setter
public class CondicaoPet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_condicao_pet")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pet", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_condicao", nullable = false)
    private TipoCondicao tipoCondicao;

    @Column(name = "dt_diagnostico", nullable = false)
    private LocalDate dtDiagnostico;

    @Column(name = "ds_observacao", length = 500)
    private String observacao;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "fl_ativo", length = 1)
    private Boolean ativo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta_origem")
    private Consulta consultaOrigem;
}
