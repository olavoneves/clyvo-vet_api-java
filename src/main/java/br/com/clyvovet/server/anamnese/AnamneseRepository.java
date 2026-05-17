package br.com.clyvovet.server.anamnese;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnamneseRepository extends JpaRepository<Anamnese, Long> {

    Optional<Anamnese> findByConsultaId(Long consultaId);
}
