package br.com.clyvovet.server.especie;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EspecieService {

    private final EspecieRepository repository;

    @Transactional(readOnly = true)
    public Page<EspecieResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(EspecieResponse::from);
    }

    @Transactional(readOnly = true)
    public EspecieResponse findById(Long id) {
        return repository.findById(id)
                .map(EspecieResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Espécie", id));
    }

    public Especie findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Espécie", id));
    }

    @Transactional
    public EspecieResponse create(EspecieRequest request) {
        Especie especie = new Especie();
        especie.setNome(request.nome());
        especie.setDescricao(request.descricao());
        return EspecieResponse.from(repository.save(especie));
    }

    @Transactional
    public EspecieResponse update(Long id, EspecieRequest request) {
        Especie especie = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Espécie", id));
        especie.setNome(request.nome());
        especie.setDescricao(request.descricao());
        return EspecieResponse.from(repository.save(especie));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Espécie", id);
        repository.deleteById(id);
    }
}
