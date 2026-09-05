package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    Page<Agendamento> findByPetId(Long petId, Pageable pageable);

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
}
