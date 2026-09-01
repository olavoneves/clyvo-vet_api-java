package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.PetPorte;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Limites de uma {@link CondicaoProt} por porte do animal, usados quando a
 * condicao esta marcada como flPorPorte. Um gigante entra na fase adulta muito
 * depois de um mini, e o protocolo precisa refletir isso.
 */
@Entity
@Table(name = "TB_CLV_PARAM_PORTE")
@Getter
@Setter
public class ParamPorte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_param_porte")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_condicao_prot", nullable = false)
    private CondicaoProt condicaoProt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_porte", nullable = false, length = 10)
    private PetPorte dsPorte;

    @Column(name = "nr_valor_min", precision = 10, scale = 2)
    private BigDecimal nrValorMin;

    @Column(name = "nr_valor_max", precision = 10, scale = 2)
    private BigDecimal nrValorMax;
}
