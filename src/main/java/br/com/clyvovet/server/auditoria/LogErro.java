package br.com.clyvovet.server.auditoria;

import br.com.clyvovet.server.enums.AmbienteLog;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_CLV_LOG_ERRO")
@Getter
@Setter
public class LogErro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_log")
    private Long id;

    @Column(name = "nm_procedure", nullable = false, length = 100)
    private String procedure;

    @Column(name = "nm_usuario", nullable = false, length = 80)
    private String usuario;

    @Column(name = "dt_ocorrencia")
    private LocalDateTime dtOcorrencia;

    @Column(name = "nr_codigo_erro", nullable = false)
    private Long codigoErro;

    @Column(name = "ds_mensagem_erro", nullable = false, length = 4000)
    private String mensagemErro;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_ambiente", length = 3)
    private AmbienteLog ambiente;
}
