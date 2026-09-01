package br.com.clyvovet.server.pet;

import br.com.clyvovet.server.enums.PetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Long> {

    // PetResponse le tutor, raca e clinica de toda linha. Como as tres sao LAZY
    // e a renderizacao acontece sem open-in-view, sem o grafo uma pagina de 20
    // pets custa 61 selects.
    @Override
    @EntityGraph(attributePaths = {"tutor", "raca", "clinica"})
    Page<Pet> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"tutor", "raca", "clinica"})
    Page<Pet> findByTutorId(Long tutorId, Pageable pageable);

    @EntityGraph(attributePaths = {"tutor", "raca", "clinica"})
    Page<Pet> findByStatus(PetStatus status, Pageable pageable);

    @Query("SELECT p FROM Pet p JOIN FETCH p.tutor JOIN FETCH p.raca r JOIN FETCH r.especie WHERE p.id = :id")
    Optional<Pet> findByIdWithDetails(@Param("id") Long id);
}
