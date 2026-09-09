package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    Page<Agendamento> findByPetId(Long petId, Pageable pageable);

    /**
     * Os compromissos dos pets de um tutor.
     *
     * <p>O grafo carrega pet e veterinario porque {@code AgendamentoResponse} le
     * o nome dos dois em toda linha, e as duas associacoes sao LAZY sem
     * open-in-view: sem ele uma pagina de 20 compromissos custa 41 selects.
     */
    @EntityGraph(attributePaths = {"pet", "veterinario"})
    Page<Agendamento> findByPet_Tutor_Id(Long idTutor, Pageable pageable);

    /**
     * De quem e o pet deste compromisso, em uma consulta e sem carregar a
     * entidade — a contrapartida de {@code PetRepository.idDoTutor}.
     *
     * <p>Vazio significa tanto "nao existe" quanto "e de outra clinica": o filtro
     * de tenant do Agendamento ja recorta por pet da clinica, e quem pergunta
     * tambem nao deve distinguir os dois casos.
     */
    @Query("select a.pet.tutor.id from Agendamento a where a.id = :id")
    Optional<Long> idDoTutor(@Param("id") Long id);

    Page<Agendamento> findByStatus(AgendamentoStatus status, Pageable pageable);

    Page<Agendamento> findByVeterinarioId(Long veterinarioId, Pageable pageable);

    /**
     * Compromissos que de fato ocupam a agenda numa faixa de datas.
     *
     * <p>Cancelado nao ocupa nada: o horario volta a ser oferecido. Status nulo
     * ocupa — linha antiga sem status ainda e uma consulta marcada, e oferecer
     * esse horario para outro tutor produziria duas pessoas na mesma sala.
     */
    @Query("""
            select a from Agendamento a
             where a.dtAgendamento between :de and :ate
               and (a.status is null or a.status <> br.com.clyvovet.server.enums.AgendamentoStatus.CANCELADO)
            """)
    List<Agendamento> ocupadosEntre(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** O mesmo recorte, para um unico horario de um unico veterinario. */
    @Query("""
            select a from Agendamento a
             where a.veterinario.id = :idVeterinario
               and a.dtAgendamento = :data
               and a.hrAgendamento = :hora
               and (a.status is null or a.status <> br.com.clyvovet.server.enums.AgendamentoStatus.CANCELADO)
            """)
    List<Agendamento> ocupando(@Param("idVeterinario") Long idVeterinario,
                               @Param("data") LocalDate data,
                               @Param("hora") String hora);

    @Query("select a from Agendamento a join fetch a.pet join fetch a.veterinario where a.id = :id")
    Optional<Agendamento> buscarComPetEVeterinario(@Param("id") Long id);

    /**
     * A agenda da clinica numa faixa de datas, com a obrigacao que originou cada
     * compromisso quando houver uma.
     *
     * <p>O join e <b>left</b>, e pela obrigacao: quem carrega a chave estrangeira
     * e {@code Obrigacao.agendamento}, e nem todo compromisso vem do motor — o que
     * o balcao marca direto nao tem obrigacao atras e ainda assim ocupa a sala.
     * Um inner join sumiria com esses.
     *
     * <p>Cancelado fica de fora pelo mesmo motivo de {@code ocupadosEntre}: nao
     * ocupa nada, e listar cancelamento na agenda do dia so gera telefonema.
     */
    @Query("""
            select new br.com.clyvovet.server.agendamento.CompromissoDaAgenda(
                     a.id, a.dtAgendamento, a.hrAgendamento, a.status, a.canalOrigem,
                     p.id, p.nome, v.nome,
                     e.nmEtapa, pr.nmProtocolo, o.dtPrevista, o.dsStatus)
              from Agendamento a
              join a.pet p
              join a.veterinario v
              left join Obrigacao o on o.agendamento = a
              left join o.etapa e
              left join o.versaoProtocolo vp
              left join vp.protocolo pr
             where a.dtAgendamento between :de and :ate
               and (a.status is null or a.status <> br.com.clyvovet.server.enums.AgendamentoStatus.CANCELADO)
             order by a.dtAgendamento asc, a.hrAgendamento asc
            """)
    List<CompromissoDaAgenda> agendaEntre(@Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
