package br.com.clyvovet.server.tutor;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.enums.CanalPreferencial;
import br.com.clyvovet.server.tenant.TenantFilters;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

@Entity
@Table(name = "TB_CLV_TUTOR")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO)
public class Tutor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tutor")
    private Long id;

    @Column(name = "nm_tutor", nullable = false, length = 100)
    private String nome;

    @Column(name = "ds_email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "nr_telefone", length = 20)
    private String telefone;

    @Column(name = "nr_telefone_emergencia", length = 20)
    private String telefoneEmergencia;

    @Column(name = "ds_senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_canal_preferencial", length = 10)
    private CanalPreferencial canalPreferencial;

    @Column(name = "dt_cadastro")
    private LocalDate dtCadastro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clinica", nullable = false)
    private Clinica clinica;
}
