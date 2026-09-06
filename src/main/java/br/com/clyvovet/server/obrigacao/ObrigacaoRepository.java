package br.com.clyvovet.server.obrigacao;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ObrigacaoRepository
        extends JpaRepository<Obrigacao, Long>, JpaSpecificationExecutor<Obrigacao> {

    // a listagem sempre mostra pet, etapa e protocolo: sem o grafo, uma pagina
    // de 20 obrigacoes vira 61 selects
    @Override
    @EntityGraph(attributePaths = {"pet", "etapa", "etapa.versaoProtocolo", "etapa.versaoProtocolo.protocolo"})
    Page<Obrigacao> findAll(Specification<Obrigacao> spec, Pageable pageable);

    /** A obrigacao que este agendamento atende, quando ha uma. */
    Optional<Obrigacao> findByAgendamentoId(Long agendamentoId);

    /**
     * Obrigacoes de uma clinica cuja janela de lembrete ja abriu.
     *
     * <p>Tres filtros, e cada um responde por um jeito diferente de a varredura
     * dar errado:
     *
     * <ul>
     *   <li><b>{@code fl_grupo_controle = 'N'}</b> — o mais importante. O grupo de
     *   controle existe para medir o produto contra a inercia; notificar um pet
     *   de controle destroi o experimento de forma irreversivel, porque nao ha
     *   como "desnotificar". A leitura e da flag <i>persistida</i>, sorteada uma
     *   vez por FN_CLV_GRUPO_CONTROLE na geracao: recalcular o hash em Java
     *   criaria uma segunda fonte da mesma verdade, e bastaria uma diferenca de
     *   arredondamento para os dois discordarem sobre quem e controle.
     *   <li><b>{@code ds_status = 'PREVISTA'}</b> — e tambem a idempotencia: a
     *   transicao tira a obrigacao deste conjunto, entao a segunda passada nao
     *   a encontra mais.
     *   <li><b>a antecedencia do protocolo</b> — nao uma constante. Cada
     *   protocolo diz com quantos dias avisa, e a conta e do banco.
     * </ul>
     *
     * <p>E nativa por causa da aritmetica de datas, que HQL nao expressa com o
     * numero de dias vindo de uma coluna. Consulta nativa <b>nao</b> enxerga o
     * {@code @Filter} de tenant — por isso a clinica entra explicitamente no
     * WHERE, e nao por confianca no ThreadLocal.
     */
    @Query(value = """
            select o.id_obrigacao
              from TB_CLV_OBRIGACAO o
              join TB_CLV_ETAPA_PROTOCOLO e  on e.id_etapa = o.id_etapa
              join TB_CLV_VERSAO_PROTOCOLO v on v.id_versao_protocolo = e.id_versao_protocolo
              join TB_CLV_PROTOCOLO p        on p.id_protocolo = v.id_protocolo
             where o.id_clinica = :clinica
               and o.ds_status = 'PREVISTA'
               and o.fl_grupo_controle = 'N'
               and o.dt_prevista - p.nr_antecedencia_lembrete_dias <= TRUNC(SYSDATE)
             order by o.dt_prevista, o.id_obrigacao
            """, nativeQuery = true)
    List<Long> idsComLembreteEmAberto(@Param("clinica") Long clinica, Limit limite);
}
