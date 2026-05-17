package br.com.clyvovet.server.consulta;

import br.com.clyvovet.server.enums.ConsultaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    Page<Consulta> findByPetId(Long petId, Pageable pageable);

    Page<Consulta> findByStatus(ConsultaStatus status, Pageable pageable);

    Page<Consulta> findByVeterinarioId(Long veterinarioId, Pageable pageable);
}
