package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.tenant.TenantFilters;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Registro de cada mudanca de status de uma obrigacao. Escrito por
 * PR_CLV_TRANSITAR_OBRIGACAO; somente leitura pela aplicacao.
 *
 * <p>dsStatusDe nulo marca a transicao de criacao.
 */
@Entity
@Table(name = "TB_CLV_OBRIGACAO_TRANSICAO")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO)
public class ObrigacaoTransicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transicao")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clinica", nullable = false)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_obrigacao", nullable = false)
    private Obrigacao obrigacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status_de", length = 12)
    private ObrigacaoStatus dsStatusDe;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status_para", nullable = false, length = 12)
    private ObrigacaoStatus dsStatusPara;

    @Column(name = "ds_motivo", length = 300)
    private String dsMotivo;

    @Column(name = "ds_causation_id", length = 64)
    private String dsCausationId;

    @Column(name = "nm_usuario", length = 80)
    private String nmUsuario;

    @Column(name = "dt_ocorrencia", nullable = false)
    private LocalDateTime dtOcorrencia;
}
