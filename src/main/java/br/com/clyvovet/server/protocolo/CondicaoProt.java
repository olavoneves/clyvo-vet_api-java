package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.converter.SimNaoConverter;
import br.com.clyvovet.server.enums.AtributoCondicao;
import br.com.clyvovet.server.enums.OperadorCondicao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Uma comparacao sobre um atributo do pet, dentro de uma {@link RegraAtivacao}.
 *
 * <p>Quando flPorPorte e verdadeiro, os limites nao vem de nrValorMin/Max e sim
 * de {@link ParamPorte}: "filhote" nao significa a mesma idade para um chihuahua
 * e para um dogue alemao. Avaliada por FN_CLV_AVALIA_CONDICAO.
 */
@Entity
@Table(name = "TB_CLV_CONDICAO_PROT")
@Getter
@Setter
public class CondicaoProt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_condicao_prot")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_regra_ativacao", nullable = false)
    private RegraAtivacao regraAtivacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_atributo", nullable = false, length = 20)
    private AtributoCondicao dsAtributo;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_operador", nullable = false, length = 10)
    private OperadorCondicao dsOperador;

    /** Para EQ/NEQ/IN/NOT_IN sobre atributo textual. Lista separada por virgula. */
    @Column(name = "ds_valor_texto", length = 400)
    private String dsValorTexto;

    @Column(name = "nr_valor_min", precision = 10, scale = 2)
    private BigDecimal nrValorMin;

    @Column(name = "nr_valor_max", precision = 10, scale = 2)
    private BigDecimal nrValorMax;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "fl_por_porte", nullable = false, columnDefinition = "CHAR(1)")
    private Boolean flPorPorte;
}
