package br.com.clyvovet.server.raca;

import br.com.clyvovet.server.especie.EspecieService;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RacaService {

    private final RacaRepository repository;
    private final EspecieService especieService;

    @Transactional(readOnly = true)
    public Page<RacaResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(RacaResponse::from);
    }

    @Transactional(readOnly = true)
    public RacaResponse findById(Long id) {
        return repository.findById(id)
                .map(RacaResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Raça", id));
    }

    public Raca findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Raça", id));
    }

    @Transactional(readOnly = true)
    public Page<RacaResponse> findByEspecie(Long especieId, Pageable pageable) {
        return repository.findByEspecieId(especieId, pageable).map(RacaResponse::from);
    }

    @Transactional
    public RacaResponse create(RacaRequest request) {
        Raca r = new Raca();
        r.setNome(request.nome());
        r.setEspecie(especieService.findEntityById(request.especieId()));
        return RacaResponse.from(repository.save(r));
    }

    @Transactional
    public RacaResponse update(Long id, RacaRequest request) {
        Raca r = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Raça", id));
        r.setNome(request.nome());
        r.setEspecie(especieService.findEntityById(request.especieId()));
        return RacaResponse.from(repository.save(r));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Raça", id);
        repository.deleteById(id);
    }
}
