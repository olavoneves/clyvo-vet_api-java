package br.com.clyvovet.server.anamnese;

import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.enums.CondicaoCorporal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "TB_CLV_ANAMNESE")
@Getter
@Setter
public class Anamnese {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_anamnese")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = false, unique = true)
    private Consulta consulta;

    @Column(name = "ds_queixa_principal", nullable = false, length = 500)
    private String queixaPrincipal;

    @Column(name = "ds_historico_relatado", length = 2000)
    private String historicoRelatado;

    @Column(name = "nr_peso_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "nr_temperatura_c", precision = 4, scale = 1)
    private BigDecimal temperaturaC;

    @Column(name = "nr_freq_cardiaca")
    private Integer freqCardiaca;

    @Column(name = "nr_freq_respiratoria")
    private Integer freqRespiratoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_condicao_corporal", length = 12)
    private CondicaoCorporal condicaoCorporal;

    @Column(name = "ds_mucosas", length = 100)
    private String mucosas;

    @Column(name = "ds_linfonodos", length = 100)
    private String linfonodos;

    @Column(name = "ds_observacoes_clinicas", length = 2000)
    private String observacoesClinicas;
}
