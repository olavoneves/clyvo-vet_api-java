package br.com.clyvovet.server.iot.alerta;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertaIotRepository extends JpaRepository<AlertaIot, Long> {

    Page<AlertaIot> findByResolvido(Boolean resolvido, Pageable pageable);

    @Query("SELECT a FROM AlertaIot a WHERE a.leitura.sensor.pet.id = :petId AND a.resolvido = false")
    Page<AlertaIot> findUnresolvedByPetId(@Param("petId") Long petId, Pageable pageable);
}
