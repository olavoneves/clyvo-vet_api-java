package br.com.clyvovet.server.iot.sensor;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.pet.PetService;
import br.com.clyvovet.server.tiposensor.TipoSensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SensorIotService {

    private final SensorIotRepository repository;
    private final TipoSensorService tipoSensorService;
    private final PetService petService;

    @Transactional(readOnly = true)
    public Page<SensorIotResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(SensorIotResponse::from);
    }

    @Transactional(readOnly = true)
    public SensorIotResponse findById(Long id) {
        return repository.findById(id)
                .map(SensorIotResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Sensor IoT", id));
    }

    public SensorIot findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sensor IoT", id));
    }

    @Transactional(readOnly = true)
    public Page<SensorIotResponse> findByPet(Long petId, Pageable pageable) {
        return repository.findByPetId(petId, pageable).map(SensorIotResponse::from);
    }

    @Transactional
    public SensorIotResponse create(SensorIotRequest request) {
        SensorIot s = new SensorIot();
        mapRequest(s, request);
        return SensorIotResponse.from(repository.save(s));
    }

    @Transactional
    public SensorIotResponse update(Long id, SensorIotRequest request) {
        SensorIot s = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sensor IoT", id));
        mapRequest(s, request);
        return SensorIotResponse.from(repository.save(s));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Sensor IoT", id);
        repository.deleteById(id);
    }

    private void mapRequest(SensorIot s, SensorIotRequest r) {
        s.setNome(r.nome());
        s.setTipoSensor(tipoSensorService.findEntityById(r.tipoSensorId()));
        s.setPet(petService.findEntityById(r.petId()));
        s.setDtInstalacao(r.dtInstalacao());
        s.setDtUltimaGalibracao(r.dtUltimaCalibracao());
        s.setAtivo(r.ativo() != null ? r.ativo() : true);
    }
}
