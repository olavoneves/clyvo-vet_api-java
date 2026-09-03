package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.agendamento.Agendamento;
import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.converter.SimNaoConverter;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.protocolo.EtapaProtocolo;
import br.com.clyvovet.server.protocolo.VersaoProtocolo;
import br.com.clyvovet.server.tenant.TenantFilters;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * O compromisso de cuidado que o protocolo gerou para um pet: o que era para
 * acontecer, quando, e o que de fato aconteceu.
 *
 * <p>Escrita exclusivamente pelas procedures PR_CLV_GERAR_OBRIGACOES e
 * PR_CLV_TRANSITAR_OBRIGACAO. A aplicacao le esta tabela, mas nao muda status
 * por UPDATE: a transicao valida, registra em TB_CLV_OBRIGACAO_TRANSICAO e
 * publica no outbox, tudo em uma transacao no banco.
 *
 * <p>flGrupoControle marca a obrigacao sorteada para o grupo de controle, que
 * nao recebe lembrete. E o que permite dizer quanto da receita foi recuperada
 * pelo produto e quanto teria acontecido de qualquer jeito.
 */
@Entity
@Table(name = "TB_CLV_OBRIGACAO")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO)
public class Obrigacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_obrigacao")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clinica", nullable = false)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pet", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_versao_protocolo", nullable = false)
    private VersaoProtocolo versaoProtocolo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_etapa", nullable = false)
    private EtapaProtocolo etapa;

    /** Consulta cujo registro disparou a geracao. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta_origem")
    private Consulta consultaOrigem;

    @Column(name = "dt_prevista", nullable = false)
    private LocalDate dtPrevista;

    @Column(name = "dt_janela_inicio", nullable = false)
    private LocalDate dtJanelaInicio;

    @Column(name = "dt_janela_fim", nullable = false)
    private LocalDate dtJanelaFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status", nullable = false, length = 12)
    private ObrigacaoStatus dsStatus;

    /** Amarra a obrigacao a cadeia de eventos que a originou. */
    @Column(name = "ds_correlation_id", nullable = false, length = 64)
    private String dsCorrelationId;

    @Column(name = "ds_causation_id", length = 64)
    private String dsCausationId;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "fl_grupo_controle", nullable = false, columnDefinition = "CHAR(1)")
    private Boolean flGrupoControle;

    @Column(name = "ds_sorteio_seed", length = 128)
    private String dsSorteioSeed;

    /** Torna o sorteio do grupo de controle auditavel depois do fato. */
    @Column(name = "ds_hash_sorteio", length = 64)
    private String dsHashSorteio;

    @Column(name = "nr_versao_sorteio")
    private Integer nrVersaoSorteio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agendamento")
    private Agendamento agendamento;

    /** Consulta que efetivamente cumpriu a obrigacao. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta_destino")
    private Consulta consultaDestino;

    @Column(name = "nr_valor_estimado", precision = 10, scale = 2)
    private BigDecimal nrValorEstimado;

    @Column(name = "nr_valor_realizado", precision = 10, scale = 2)
    private BigDecimal nrValorRealizado;

    @Column(name = "dt_notificada")
    private LocalDateTime dtNotificada;

    @Column(name = "dt_respondida")
    private LocalDateTime dtRespondida;

    @Column(name = "dt_agendada")
    private LocalDateTime dtAgendada;

    @Column(name = "dt_cumprida")
    private LocalDateTime dtCumprida;

    @Column(name = "dt_criacao", nullable = false)
    private LocalDateTime dtCriacao;

    @Column(name = "dt_atualizacao", nullable = false)
    private LocalDateTime dtAtualizacao;
}
