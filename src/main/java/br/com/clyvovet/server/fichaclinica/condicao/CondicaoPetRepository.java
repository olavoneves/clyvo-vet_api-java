package br.com.clyvovet.server.fichaclinica.condicao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CondicaoPetRepository extends JpaRepository<CondicaoPet, Long> {

    List<CondicaoPet> findByPetIdAndAtivoTrue(Long petId);

    Page<CondicaoPet> findByPetId(Long petId, Pageable pageable);
}
