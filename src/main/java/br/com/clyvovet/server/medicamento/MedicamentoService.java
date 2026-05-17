package br.com.clyvovet.server.medicamento;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicamentoService {

    private final MedicamentoRepository repository;

    @Transactional(readOnly = true)
    public Page<MedicamentoResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(MedicamentoResponse::from);
    }

    @Transactional(readOnly = true)
    public MedicamentoResponse findById(Long id) {
        return repository.findById(id)
                .map(MedicamentoResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Medicamento", id));
    }

    public Medicamento findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Medicamento", id));
    }

    @Transactional
    public MedicamentoResponse create(MedicamentoRequest request) {
        Medicamento m = new Medicamento();
        m.setNome(request.nome());
        m.setPrincipioAtivo(request.principioAtivo());
        m.setClasseTerapeutica(request.classeTerapeutica());
        return MedicamentoResponse.from(repository.save(m));
    }

    @Transactional
    public MedicamentoResponse update(Long id, MedicamentoRequest request) {
        Medicamento m = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Medicamento", id));
        m.setNome(request.nome());
        m.setPrincipioAtivo(request.principioAtivo());
        m.setClasseTerapeutica(request.classeTerapeutica());
        return MedicamentoResponse.from(repository.save(m));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Medicamento", id);
        repository.deleteById(id);
    }
}
