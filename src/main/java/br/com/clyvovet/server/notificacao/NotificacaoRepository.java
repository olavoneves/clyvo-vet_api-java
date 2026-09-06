package br.com.clyvovet.server.notificacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    /**
     * A caixa de entrada do tutor: nao lidas primeiro, mais recentes antes.
     *
     * <p>A ordem sai daqui e nao da tela porque e ela que o indice
     * {@code ix_clv_notif_caixa} cobre — ordenar em memoria depois de ler tudo
     * funcionaria hoje, com dezenas de linhas, e pararia de funcionar sem aviso.
     *
     * <p>{@code join fetch} nos dois lados: a tela mostra o nome do pet e o que
     * esta sendo cobrado em cada linha, e sem isso cada notificacao vira duas
     * consultas a mais.
     */
    @Query("""
            select n from Notificacao n
              left join fetch n.obrigacao o
              left join fetch o.pet
              left join fetch o.etapa
             where n.tutor.id = :idTutor
             order by case when n.dtLeitura is null then 0 else 1 end,
                      n.dtEmissao desc
            """)
    List<Notificacao> daCaixaDo(@Param("idTutor") Long idTutor);

    long countByTutorIdAndDtLeituraIsNull(Long idTutor);

    /**
     * A notificacao pedida, se for deste tutor.
     *
     * <p>O dono entra na propria consulta, e nao numa checagem depois: e a mesma
     * regra que vale para o agente — quem pede so alcanca o que e seu, e um id de
     * outro tutor simplesmente nao existe.
     */
    Optional<Notificacao> findByIdAndTutorId(Long id, Long idTutor);

    boolean existsByObrigacaoIdAndCanal(Long idObrigacao,
                                        br.com.clyvovet.server.enums.CanalPreferencial canal);
}
