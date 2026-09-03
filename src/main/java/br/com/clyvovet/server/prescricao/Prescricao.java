package br.com.clyvovet.server.prescricao;

import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.medicamento.Medicamento;
import br.com.clyvovet.server.tenant.TenantFilters;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "TB_CLV_PRESCRICAO")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO_VIA_CONSULTA)
public class Prescricao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prescricao")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = false)
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medicamento", nullable = false)
    private Medicamento medicamento;

    @Column(name = "ds_dosagem", nullable = false, length = 100)
    private String dosagem;

    @Column(name = "ds_frequencia", length = 100)
    private String frequencia;

    @Column(name = "nr_duracao_dias")
    private Integer duracaoDias;
}
