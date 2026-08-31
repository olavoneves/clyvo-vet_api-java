package br.com.clyvovet.server.clinica;

import br.com.clyvovet.server.tenant.TenantFilters;
import br.com.clyvovet.server.tenant.TenantIdResolver;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;

/**
 * Raiz do tenant. Nao carrega o filtro: e a propria clinica que o define.
 *
 * <p>O FilterDef vive aqui, ao lado do agregado que ele delimita. autoEnabled
 * liga o filtro em toda sessao do Hibernate, sem depender de alguem lembrar de
 * ativa-lo; applyToLoadByKey estende o filtro ao find() por id, senao
 * GET /pets/[id] vazaria entre clinicas.
 */
@Entity
@Table(name = "TB_CLV_CLINICA")
@Getter
@Setter
@FilterDef(
        name = TenantFilters.TENANT,
        autoEnabled = true,
        applyToLoadByKey = true,
        parameters = @ParamDef(
                name = TenantFilters.PARAM_ID_CLINICA,
                type = Long.class,
                resolver = TenantIdResolver.class))
public class Clinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_clinica")
    private Long id;

    @Column(name = "nm_clinica", nullable = false, length = 120)
    private String nome;

    @Column(name = "ds_cnpj", nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(name = "ds_logradouro", nullable = false, length = 200)
    private String logradouro;

    @Column(name = "ds_numero", length = 10)
    private String numero;

    @Column(name = "ds_bairro", length = 80)
    private String bairro;

    @Column(name = "nm_cidade", nullable = false, length = 80)
    private String cidade;

    @Column(name = "sg_estado", nullable = false, length = 2)
    private String estado;

    @Column(name = "nr_cep", length = 8)
    private String cep;

    @Column(name = "nr_telefone", length = 20)
    private String telefone;

    @Column(name = "nr_ticket_medio", nullable = false, precision = 10, scale = 2)
    private BigDecimal nrTicketMedio = BigDecimal.ZERO;
}
