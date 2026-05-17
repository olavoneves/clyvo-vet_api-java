package br.com.clyvovet.server.tutor;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TutorService {

    private final TutorRepository repository;

    @Transactional(readOnly = true)
    public Page<TutorResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(TutorResponse::from);
    }

    @Transactional(readOnly = true)
    public TutorResponse findById(Long id) {
        return repository.findById(id)
                .map(TutorResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Tutor", id));
    }

    public Tutor findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tutor", id));
    }

    @Transactional
    public TutorResponse create(TutorRequest request) {
        Tutor t = new Tutor();
        t.setNome(request.nome());
        t.setEmail(request.email());
        t.setTelefone(request.telefone());
        t.setTelefoneEmergencia(request.telefoneEmergencia());
        t.setSenhaHash(request.senhaHash());
        t.setCanalPreferencial(request.canalPreferencial());
        t.setDtCadastro(LocalDate.now());
        return TutorResponse.from(repository.save(t));
    }

    @Transactional
    public TutorResponse update(Long id, TutorRequest request) {
        Tutor t = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tutor", id));
        t.setNome(request.nome());
        t.setEmail(request.email());
        t.setTelefone(request.telefone());
        t.setTelefoneEmergencia(request.telefoneEmergencia());
        t.setSenhaHash(request.senhaHash());
        t.setCanalPreferencial(request.canalPreferencial());
        return TutorResponse.from(repository.save(t));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Tutor", id);
        repository.deleteById(id);
    }
}
