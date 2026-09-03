package br.com.clyvovet.server.consulta;

import br.com.clyvovet.server.enums.ConsultaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    // ConsultaResponse le pet e veterinario de toda linha: sem o grafo, cada
    // consulta listada custa dois selects a mais
    @Override
    @EntityGraph(attributePaths = {"pet", "veterinario"})
    Page<Consulta> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"pet", "veterinario"})
    Page<Consulta> findByPetId(Long petId, Pageable pageable);

    @EntityGraph(attributePaths = {"pet", "veterinario"})
    Page<Consulta> findByStatus(ConsultaStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"pet", "veterinario"})
    Page<Consulta> findByVeterinarioId(Long veterinarioId, Pageable pageable);
}
