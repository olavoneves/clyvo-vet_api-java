package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.converter.SimNaoConverter;
import br.com.clyvovet.server.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_CLV_REFRESH_TOKEN")
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_refresh_token")
    private Long id;

    @Column(name = "ds_token", nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_tipo_usuario", nullable = false, length = 15)
    private TipoUsuario tipoUsuario;

    @Column(name = "nr_id_usuario", nullable = false)
    private Long idUsuario;

    @Column(name = "dt_expiracao", nullable = false)
    private LocalDateTime dtExpiracao;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "fl_revogado", nullable = false, length = 1)
    private Boolean revogado;

    @Column(name = "dt_criacao", nullable = false)
    private LocalDateTime dtCriacao;
}
