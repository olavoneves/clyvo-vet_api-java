package br.com.clyvovet.server.tipovacina;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TipoVacinaService {

    private final TipoVacinaRepository repository;

    @Transactional(readOnly = true)
    public Page<TipoVacinaResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(TipoVacinaResponse::from);
    }

    @Transactional(readOnly = true)
    public TipoVacinaResponse findById(Long id) {
        return repository.findById(id)
                .map(TipoVacinaResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Vacina", id));
    }

    public TipoVacina findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Vacina", id));
    }

    @Transactional
    public TipoVacinaResponse create(TipoVacinaRequest request) {
        TipoVacina tv = new TipoVacina();
        tv.setNome(request.nome());
        tv.setFabricante(request.fabricante());
        tv.setDoseIntervaloDias(request.doseIntervaloDias());
        return TipoVacinaResponse.from(repository.save(tv));
    }

    @Transactional
    public TipoVacinaResponse update(Long id, TipoVacinaRequest request) {
        TipoVacina tv = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Vacina", id));
        tv.setNome(request.nome());
        tv.setFabricante(request.fabricante());
        tv.setDoseIntervaloDias(request.doseIntervaloDias());
        return TipoVacinaResponse.from(repository.save(tv));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Tipo de Vacina", id);
        repository.deleteById(id);
    }
}
