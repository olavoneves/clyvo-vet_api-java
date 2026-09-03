package br.com.clyvovet.server.vacinacao;

import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.tenant.TenantFilters;
import br.com.clyvovet.server.tipovacina.TipoVacina;
import br.com.clyvovet.server.veterinario.Veterinario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

@Entity
@Table(name = "TB_CLV_APLICACAO_VACINA")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO_VIA_PET)
public class AplicacaoVacina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aplicacao")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pet", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_vacina", nullable = false)
    private TipoVacina tipoVacina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_veterinario", nullable = false)
    private Veterinario veterinario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta")
    private Consulta consulta;

    @Column(name = "dt_aplicacao", nullable = false)
    private LocalDate dtAplicacao;

    @Column(name = "nr_dose")
    private Integer numeroDose;

    @Column(name = "nr_lote", length = 30)
    private String lote;
}
