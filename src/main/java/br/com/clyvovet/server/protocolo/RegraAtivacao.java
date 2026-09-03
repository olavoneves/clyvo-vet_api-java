package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.EventoGatilho;
import br.com.clyvovet.server.enums.OperadorLogico;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Decide se uma versao de protocolo se aplica ao pet quando o gatilho ocorre.
 * As condicoes sao combinadas por dsOperadorLogico; nrPrioridade desempata
 * quando mais de uma regra casa. Avaliada por FN_CLV_AVALIA_REGRA.
 */
@Entity
@Table(name = "TB_CLV_REGRA_ATIVACAO")
@Getter
@Setter
public class RegraAtivacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regra_ativacao")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_versao_protocolo", nullable = false)
    private VersaoProtocolo versaoProtocolo;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_evento_gatilho", nullable = false, length = 30)
    private EventoGatilho dsEventoGatilho;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_operador_logico", nullable = false, length = 3)
    private OperadorLogico dsOperadorLogico;

    @Column(name = "nr_prioridade", nullable = false)
    private Integer nrPrioridade;
}
