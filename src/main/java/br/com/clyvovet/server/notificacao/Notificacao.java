package br.com.clyvovet.server.notificacao;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.enums.CanalPreferencial;
import br.com.clyvovet.server.obrigacao.Obrigacao;
import br.com.clyvovet.server.tenant.TenantFilters;
import br.com.clyvovet.server.tutor.Tutor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Um lembrete que chegou ao tutor.
 *
 * <p>E o elo que faltava na cadeia de valor: ate aqui a obrigacao transitava
 * para NOTIFICADA e nada acontecia — o sistema registrava que um aviso deveria
 * ter saido e ninguem guardava se saiu, para quem, por onde.
 *
 * <p>{@code dtLeitura} nula e o estado "nao lida". Nao ha flag separada de
 * proposito: dois campos para o mesmo fato divergem no dia em que alguem
 * escrever um sem o outro, e a data e o que a tela quer mostrar de qualquer
 * forma.
 */
@Entity
@Table(name = "TB_CLV_NOTIFICACAO")
@Getter
@Setter
@Filter(name = TenantFilters.TENANT, condition = TenantFilters.CONDICAO)
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacao")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clinica", nullable = false)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tutor", nullable = false)
    private Tutor tutor;

    /** Nula quando o aviso nao nasce de uma obrigacao. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_obrigacao")
    private Obrigacao obrigacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ds_canal", nullable = false, length = 15)
    private CanalPreferencial canal;

    @Column(name = "ds_titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "ds_mensagem", nullable = false, length = 500)
    private String mensagem;

    @Column(name = "dt_emissao", nullable = false)
    private LocalDateTime dtEmissao;

    @Column(name = "dt_leitura")
    private LocalDateTime dtLeitura;

    public boolean naoLida() {
        return dtLeitura == null;
    }

    /** Idempotente: reabrir uma notificacao nao reescreve quando ela foi lida. */
    public void marcarLida() {
        if (dtLeitura == null) {
            dtLeitura = LocalDateTime.now();
        }
    }
}
