package br.com.clyvovet.server.outbox;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.enums.OutboxStatus;
import br.com.clyvovet.server.tenant.TenantFilters;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Evento de dominio gravado na mesma transacao que o mudou o agregado, para
 * ser publicado depois. E o que garante que "obrigacao virou NOTIFICADA" e
 * "lembrete enviado" nao possam divergir por falha de rede.
 *
 * <p>A aplicacao le e marca como publicado; quem escreve o evento e o motor.
 */
@Entity
@Table(name = "TB_CLV_OUTBOX_EVENT")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long id;

    @Column(name = "ds_event_uuid", nullable = false, length = 36)
    private String dsEventUuid;

    @Column(name = "ds_event_type", nullable = false, length = 60)
    private String dsEventType;

    @Column(name = "nr_event_version", nullable = false)
    private Integer nrEventVersion;

    @Column(name = "ds_correlation_id", nullable = false, length = 64)
    private String dsCorrelationId;

    @Column(name = "ds_causation_id", length = 64)
    private String dsCausationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clinica", nullable = false)
    private Clinica clinica;

    @Column(name = "nm_usuario", length = 80)
    private String nmUsuario;

    @Column(name = "ds_agregado", nullable = false, length = 40)
    private String dsAgregado;

    @Column(name = "nr_id_agregado", nullable = false)
    private Long nrIdAgregado;

    @Lob
    @Column(name = "ds_payload", nullable = false)
    private String dsPayload;

    @Column(name = "dt_ocorrencia", nullable = false)
    private LocalDateTime dtOcorrencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status", nullable = false, length = 10)
    private OutboxStatus dsStatus;

    @Column(name = "nr_tentativas", nullable = false)
    private Integer nrTentativas;

    @Column(name = "dt_publicacao")
    private LocalDateTime dtPublicacao;

    @Column(name = "ds_ultimo_erro", length = 400)
    private String dsUltimoErro;
}
