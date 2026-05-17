package br.com.clyvovet.server.raca;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RacaRepository extends JpaRepository<Raca, Long> {

    Page<Raca> findByEspecieId(Long especieId, Pageable pageable);
}
