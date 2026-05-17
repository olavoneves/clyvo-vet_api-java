package br.com.clyvovet.server.fichaclinica.alergia;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlergiaPetRepository extends JpaRepository<AlergiaPet, Long> {

    List<AlergiaPet> findByPetId(Long petId);

    Page<AlergiaPet> findByPetId(Long petId, Pageable pageable);
}
