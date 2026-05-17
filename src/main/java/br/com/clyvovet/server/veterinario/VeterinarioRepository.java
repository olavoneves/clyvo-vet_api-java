package br.com.clyvovet.server.veterinario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VeterinarioRepository extends JpaRepository<Veterinario, Long> {

    Page<Veterinario> findByClinicaId(Long clinicaId, Pageable pageable);
}
