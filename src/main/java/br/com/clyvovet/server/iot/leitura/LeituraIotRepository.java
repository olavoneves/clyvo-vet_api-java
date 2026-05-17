package br.com.clyvovet.server.iot.leitura;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeituraIotRepository extends JpaRepository<LeituraIot, Long> {

    Page<LeituraIot> findBySensorId(Long sensorId, Pageable pageable);

    @Query("SELECT l FROM LeituraIot l WHERE l.sensor.pet.id = :petId ORDER BY l.dtLeitura DESC")
    Page<LeituraIot> findByPetId(@Param("petId") Long petId, Pageable pageable);
}
