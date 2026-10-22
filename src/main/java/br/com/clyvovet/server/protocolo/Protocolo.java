package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.converter.SimNaoConverter;
import br.com.clyvovet.server.enums.ProtocoloCategoria;
import br.com.clyvovet.server.enums.ProtocoloEspecie;
import br.com.clyvovet.server.tenant.TenantFilters;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Cabecalho do protocolo clinico. O conteudo versionado fica em
 * {@link VersaoProtocolo}: mudar um protocolo em uso nunca reescreve o
 * historico, cria uma versao nova.
 *
 * <p>clinica nula significa protocolo global, valido para toda a base. Por isso
 * o filtro de tenant aqui tolera NULL — ver TenantFilters.CONDICAO_CATALOGO.
 */
@Entity
@Table(name = "TB_CLV_PROTOCOLO")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO_CATALOGO)
public class Protocolo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_protocolo")
    private Long id;

    /** Nulo = protocolo global, mantido pela plataforma. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clinica")
    private Clinica clinica;

    @Column(name = "ds_codigo", nullable = false, length = 40)
    private String dsCodigo;

    @Column(name = "nm_protocolo", nullable = false, length = 160)
    private String nmProtocolo;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_especie", nullable = false, length = 10)
    private ProtocoloEspecie dsEspecie;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_categoria", nullable = false, length = 20)
    private ProtocoloCategoria dsCategoria;

    @Column(name = "ds_descricao", length = 600)
    private String dsDescricao;

    /**
     * Dias antes de {@code dt_prevista} em que o lembrete sai.
     *
     * <p>E regra clinica, e por isso mora aqui e nao numa constante em Java: a
     * clinica que quiser avisar cirurgia com 20 dias muda uma linha do catalogo,
     * sem deploy. A varredura de lembretes le esta coluna e nada mais.
     */
    @Column(name = "nr_antecedencia_lembrete_dias", nullable = false)
    private Integer nrAntecedenciaLembreteDias;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "fl_ativo", nullable = false, columnDefinition = "CHAR(1)")
    private Boolean flAtivo;

    @Column(name = "dt_criacao", nullable = false)
    private LocalDateTime dtCriacao;
}
