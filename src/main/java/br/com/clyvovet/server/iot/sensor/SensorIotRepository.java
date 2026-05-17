package br.com.clyvovet.server.iot.sensor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorIotRepository extends JpaRepository<SensorIot, Long> {

    Page<SensorIot> findByPetId(Long petId, Pageable pageable);

    Page<SensorIot> findByAtivo(Boolean ativo, Pageable pageable);
}
