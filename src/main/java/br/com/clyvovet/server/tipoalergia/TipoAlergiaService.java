package br.com.clyvovet.server.tipoalergia;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TipoAlergiaService {

    private final TipoAlergiaRepository repository;

    @Transactional(readOnly = true)
    public Page<TipoAlergiaResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(TipoAlergiaResponse::from);
    }

    @Transactional(readOnly = true)
    public TipoAlergiaResponse findById(Long id) {
        return repository.findById(id)
                .map(TipoAlergiaResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Alergia", id));
    }

    public TipoAlergia findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Alergia", id));
    }

    @Transactional
    public TipoAlergiaResponse create(TipoAlergiaRequest request) {
        TipoAlergia ta = new TipoAlergia();
        ta.setNome(request.nome());
        ta.setCategoria(request.categoria());
        return TipoAlergiaResponse.from(repository.save(ta));
    }

    @Transactional
    public TipoAlergiaResponse update(Long id, TipoAlergiaRequest request) {
        TipoAlergia ta = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Alergia", id));
        ta.setNome(request.nome());
        ta.setCategoria(request.categoria());
        return TipoAlergiaResponse.from(repository.save(ta));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Tipo de Alergia", id);
        repository.deleteById(id);
    }
}
