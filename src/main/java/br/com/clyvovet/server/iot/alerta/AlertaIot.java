package br.com.clyvovet.server.iot.alerta;

import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.converter.SimNaoConverter;
import br.com.clyvovet.server.enums.SeveridadeAlerta;
import br.com.clyvovet.server.iot.leitura.LeituraIot;
import br.com.clyvovet.server.tenant.TenantFilters;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_CLV_ALERTA_IOT")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO_VIA_LEITURA)
public class AlertaIot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_leitura", nullable = false)
    private LeituraIot leitura;

    @Column(name = "ds_descricao", nullable = false, length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_severidade", length = 8)
    private SeveridadeAlerta severidade;

    @Column(name = "dt_alerta", nullable = false)
    private LocalDateTime dtAlerta;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "fl_resolvido", columnDefinition = "CHAR(1)")
    private Boolean resolvido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta")
    private Consulta consulta;
}
