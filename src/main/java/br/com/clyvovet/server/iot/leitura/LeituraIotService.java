package br.com.clyvovet.server.iot.leitura;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.iot.sensor.SensorIotService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeituraIotService {

    private final LeituraIotRepository repository;
    private final SensorIotService sensorIotService;

    @Transactional(readOnly = true)
    public Page<LeituraIotResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(LeituraIotResponse::from);
    }

    @Transactional(readOnly = true)
    public LeituraIotResponse findById(Long id) {
        return repository.findById(id)
                .map(LeituraIotResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Leitura IoT", id));
    }

    public LeituraIot findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leitura IoT", id));
    }

    @Transactional(readOnly = true)
    public Page<LeituraIotResponse> findBySensor(Long sensorId, Pageable pageable) {
        return repository.findBySensorId(sensorId, pageable).map(LeituraIotResponse::from);
    }

    @Transactional
    public LeituraIotResponse create(LeituraIotRequest request) {
        LeituraIot l = new LeituraIot();
        l.setSensor(sensorIotService.findEntityById(request.sensorId()));
        l.setDtLeitura(request.dtLeitura());
        l.setValor(request.valor());
        return LeituraIotResponse.from(repository.save(l));
    }

    @Transactional
    public LeituraIotResponse update(Long id, LeituraIotRequest request) {
        LeituraIot l = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leitura IoT", id));
        l.setSensor(sensorIotService.findEntityById(request.sensorId()));
        l.setDtLeitura(request.dtLeitura());
        l.setValor(request.valor());
        return LeituraIotResponse.from(repository.save(l));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Leitura IoT", id);
        repository.deleteById(id);
    }
}
