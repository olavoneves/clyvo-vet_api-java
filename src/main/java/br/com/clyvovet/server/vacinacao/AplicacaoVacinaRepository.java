package br.com.clyvovet.server.vacinacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AplicacaoVacinaRepository extends JpaRepository<AplicacaoVacina, Long> {

    @Query("SELECT a FROM AplicacaoVacina a JOIN FETCH a.tipoVacina WHERE a.pet.id = :petId ORDER BY a.dtAplicacao DESC")
    Page<AplicacaoVacina> findByPetIdWithTipoVacina(@Param("petId") Long petId, Pageable pageable);

    Page<AplicacaoVacina> findByPetId(Long petId, Pageable pageable);
}
