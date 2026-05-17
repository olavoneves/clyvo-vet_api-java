package br.com.clyvovet.server.clinica;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TB_CLV_CLINICA")
@Getter
@Setter
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
}
