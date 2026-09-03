package br.com.clyvovet.server.veterinario;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.tenant.TenantFilters;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "TB_CLV_VETERINARIO")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO)
public class Veterinario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_veterinario")
    private Long id;

    @Column(name = "nm_veterinario", nullable = false, length = 100)
    private String nome;

    @Column(name = "nr_crmv", nullable = false, unique = true, length = 20)
    private String crmv;

    @Column(name = "ds_especialidade", length = 100)
    private String especialidade;

    @Column(name = "ds_email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "ds_senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clinica", nullable = false)
    private Clinica clinica;
}
