package br.com.clyvovet.server.tipocondicao;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TipoCondicaoService {

    private final TipoCondicaoRepository repository;

    @Transactional(readOnly = true)
    public Page<TipoCondicaoResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(TipoCondicaoResponse::from);
    }

    @Transactional(readOnly = true)
    public TipoCondicaoResponse findById(Long id) {
        return repository.findById(id)
                .map(TipoCondicaoResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Condição", id));
    }

    public TipoCondicao findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Condição", id));
    }

    @Transactional
    public TipoCondicaoResponse create(TipoCondicaoRequest request) {
        TipoCondicao tc = new TipoCondicao();
        tc.setNome(request.nome());
        tc.setCategoria(request.categoria());
        return TipoCondicaoResponse.from(repository.save(tc));
    }

    @Transactional
    public TipoCondicaoResponse update(Long id, TipoCondicaoRequest request) {
        TipoCondicao tc = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de Condição", id));
        tc.setNome(request.nome());
        tc.setCategoria(request.categoria());
        return TipoCondicaoResponse.from(repository.save(tc));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Tipo de Condição", id);
        repository.deleteById(id);
    }
}
