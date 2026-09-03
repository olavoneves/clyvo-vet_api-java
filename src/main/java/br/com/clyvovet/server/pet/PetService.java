package br.com.clyvovet.server.pet;

import br.com.clyvovet.server.clinica.ClinicaService;
import br.com.clyvovet.server.enums.PetStatus;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.raca.RacaService;
import br.com.clyvovet.server.tenant.TenantContext;
import br.com.clyvovet.server.tutor.TutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository repository;
    private final TutorService tutorService;
    private final RacaService racaService;
    private final ClinicaService clinicaService;

    @Transactional(readOnly = true)
    public Page<PetResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(PetResponse::from);
    }

    @Transactional(readOnly = true)
    public PetResponse findById(Long id) {
        return repository.findById(id)
                .map(PetResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Pet", id));
    }

    public Pet findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pet", id));
    }

    @Transactional(readOnly = true)
    public PetFichaTecnicaResponse getFichaTecnica(Long id) {
        Pet pet = repository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Pet", id));
        return PetFichaTecnicaResponse.from(pet);
    }

    @Transactional(readOnly = true)
    public Page<PetResponse> findByTutor(Long tutorId, Pageable pageable) {
        return repository.findByTutorId(tutorId, pageable).map(PetResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PetResponse> findByStatus(PetStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(PetResponse::from);
    }

    @Transactional
    public PetResponse create(PetRequest request) {
        Pet p = new Pet();
        // a clinica vem do token, nunca do corpo da requisicao
        p.setClinica(clinicaService.findEntityById(TenantContext.getOrThrow()));
        mapRequest(p, request);
        return PetResponse.from(repository.save(p));
    }

    @Transactional
    public PetResponse update(Long id, PetRequest request) {
        Pet p = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pet", id));
        mapRequest(p, request);
        return PetResponse.from(repository.save(p));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Pet", id);
        repository.deleteById(id);
    }

    private void mapRequest(Pet p, PetRequest r) {
        p.setNome(r.nome());
        p.setDtNascimento(r.dtNascimento());
        p.setSexo(r.sexo());
        p.setMicrochip(r.microchip());
        p.setRga(r.rga());
        p.setPelagem(r.pelagem());
        p.setPorte(r.porte());
        p.setCastrado(r.castrado());
        p.setStatus(r.status());
        p.setObservacaoGeral(r.observacaoGeral());
        p.setTutor(tutorService.findEntityById(r.tutorId()));
        p.setRaca(racaService.findEntityById(r.racaId()));
    }
}
