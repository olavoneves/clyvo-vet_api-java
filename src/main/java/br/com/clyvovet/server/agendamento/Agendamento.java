package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.enums.AgendamentoStatus;
import br.com.clyvovet.server.enums.CanalPreferencial;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.tenant.TenantFilters;
import br.com.clyvovet.server.veterinario.Veterinario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

@Entity
@Table(name = "TB_CLV_AGENDAMENTO")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO_VIA_PET)
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_agendamento")
    private Long id;

    @Column(name = "dt_agendamento", nullable = false)
    private LocalDate dtAgendamento;

    @Column(name = "hr_agendamento", nullable = false, length = 5)
    private String hrAgendamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status", length = 15)
    private AgendamentoStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_canal_origem", length = 10)
    private CanalPreferencial canalOrigem;

    @Column(name = "ds_motivo_cancelamento", length = 300)
    private String motivoCancelamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pet", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_veterinario", nullable = false)
    private Veterinario veterinario;

    @Column(name = "id_consulta")
    private Long idConsulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", insertable = false, updatable = false)
    private Consulta consulta;
}
