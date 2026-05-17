package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    Page<Agendamento> findByPetId(Long petId, Pageable pageable);

    Page<Agendamento> findByStatus(AgendamentoStatus status, Pageable pageable);

    Page<Agendamento> findByVeterinarioId(Long veterinarioId, Pageable pageable);
}
