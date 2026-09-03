package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.TipoIntervalo;
import br.com.clyvovet.server.tipovacina.TipoVacina;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Um passo do protocolo: o que fazer e quando. Cada obrigacao gerada aponta
 * para a etapa que a originou.
 *
 * <p>A data prevista sai de dsTipoIntervalo: APOS_REFERENCIA conta do evento
 * gatilho, APOS_ETAPA encadeia em etapaReferencia, RECORRENTE repete a cada
 * nrRecorrenciaDias. O calculo vive em FN_CLV_CALCULAR_DATA, no banco.
 */
@Entity
@Table(name = "TB_CLV_ETAPA_PROTOCOLO")
@Getter
@Setter
public class EtapaProtocolo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_etapa")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_versao_protocolo", nullable = false)
    private VersaoProtocolo versaoProtocolo;

    @Column(name = "nr_ordem", nullable = false)
    private Integer nrOrdem;

    @Column(name = "nm_etapa", nullable = false, length = 160)
    private String nmEtapa;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_tipo_intervalo", nullable = false, length = 20)
    private TipoIntervalo dsTipoIntervalo;

    /** Preenchida quando dsTipoIntervalo e APOS_ETAPA: a etapa que ancora a contagem. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_etapa_referencia")
    private EtapaProtocolo etapaReferencia;

    @Column(name = "nr_offset_dias", nullable = false)
    private Integer nrOffsetDias;

    @Column(name = "nr_janela_antes", nullable = false)
    private Integer nrJanelaAntes;

    @Column(name = "nr_janela_depois", nullable = false)
    private Integer nrJanelaDepois;

    @Column(name = "nr_recorrencia_dias")
    private Integer nrRecorrenciaDias;

    @Column(name = "ds_procedimento", length = 60)
    private String dsProcedimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_vacina")
    private TipoVacina tipoVacina;

    /** Quanto a etapa vale: base do valor estimado da obrigacao e do painel de receita. */
    @Column(name = "nr_valor_referencia", precision = 10, scale = 2)
    private BigDecimal nrValorReferencia;

    @Column(name = "nr_max_ocorrencias")
    private Integer nrMaxOcorrencias;
}
