package br.com.clyvovet.server.consulta;

import br.com.clyvovet.server.enums.ConsultaStatus;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.pet.PetService;
import br.com.clyvovet.server.veterinario.VeterinarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultaService {

    private final ConsultaRepository repository;
    private final PetService petService;
    private final VeterinarioService veterinarioService;

    @Transactional(readOnly = true)
    public Page<ConsultaResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(ConsultaResponse::from);
    }

    @Transactional(readOnly = true)
    public ConsultaResponse findById(Long id) {
        return repository.findById(id)
                .map(ConsultaResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Consulta", id));
    }

    public Consulta findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Consulta", id));
    }

    @Transactional(readOnly = true)
    public Page<ConsultaResponse> findByPet(Long petId, Pageable pageable) {
        return repository.findByPetId(petId, pageable).map(ConsultaResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ConsultaResponse> findByStatus(ConsultaStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(ConsultaResponse::from);
    }

    @Transactional
    public ConsultaResponse create(ConsultaRequest request) {
        Consulta c = new Consulta();
        mapRequest(c, request);
        return ConsultaResponse.from(repository.save(c));
    }

    @Transactional
    public ConsultaResponse update(Long id, ConsultaRequest request) {
        Consulta c = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Consulta", id));
        mapRequest(c, request);
        return ConsultaResponse.from(repository.save(c));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Consulta", id);
        repository.deleteById(id);
    }

    private void mapRequest(Consulta c, ConsultaRequest r) {
        c.setDtConsulta(r.dtConsulta());
        c.setMotivo(r.motivo());
        c.setDiagnostico(r.diagnostico());
        c.setStatus(r.status());
        c.setNrValor(r.nrValor());
        c.setPet(petService.findEntityById(r.petId()));
        c.setVeterinario(veterinarioService.findEntityById(r.veterinarioId()));
    }
}
