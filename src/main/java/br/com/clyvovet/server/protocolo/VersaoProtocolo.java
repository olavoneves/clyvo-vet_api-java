package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.VersaoProtocoloStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Versao imutavel de um protocolo. A obrigacao guarda a versao que a gerou,
 * entao trocar o protocolo amanha nao reescreve o que ja foi prometido ao tutor.
 *
 * <p>Sem filtro de tenant: o catalogo e compartilhado, e uma obrigacao de
 * qualquer clinica pode apontar para a versao de um protocolo global.
 */
@Entity
@Table(name = "TB_CLV_VERSAO_PROTOCOLO")
@Getter
@Setter
public class VersaoProtocolo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_versao_protocolo")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_protocolo", nullable = false)
    private Protocolo protocolo;

    @Column(name = "nr_versao", nullable = false)
    private Integer nrVersao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_status", nullable = false, length = 12)
    private VersaoProtocoloStatus dsStatus;

    @Column(name = "dt_vigente_de", nullable = false)
    private LocalDate dtVigenteDe;

    @Column(name = "dt_vigente_ate")
    private LocalDate dtVigenteAte;

    /** Referencia clinica que embasa o protocolo (diretriz, consenso, artigo). */
    @Column(name = "ds_fonte_clinica", length = 300)
    private String dsFonteClinica;

    @Column(name = "ds_notas", length = 600)
    private String dsNotas;
}
