package br.com.clyvovet.server.tiposensor;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TipoSensorService {

    private final TipoSensorRepository repository;

    @Transactional(readOnly = true)
    public Page<TipoSensorResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(TipoSensorResponse::from);
    }

    @Transactional(readOnly = true)
    public TipoSensorResponse findById(Long id) {
        return repository.findById(id)
                .map(TipoSensorResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Sensor", id));
    }

    public TipoSensor findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Sensor", id));
    }

    @Transactional
    public TipoSensorResponse create(TipoSensorRequest request) {
        TipoSensor ts = new TipoSensor();
        ts.setNome(request.nome());
        ts.setUnidade(request.unidade());
        ts.setValorMin(request.valorMin());
        ts.setValorMax(request.valorMax());
        return TipoSensorResponse.from(repository.save(ts));
    }

    @Transactional
    public TipoSensorResponse update(Long id, TipoSensorRequest request) {
        TipoSensor ts = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Sensor", id));
        ts.setNome(request.nome());
        ts.setUnidade(request.unidade());
        ts.setValorMin(request.valorMin());
        ts.setValorMax(request.valorMax());
        return TipoSensorResponse.from(repository.save(ts));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Tipo de Sensor", id);
        repository.deleteById(id);
    }
}
