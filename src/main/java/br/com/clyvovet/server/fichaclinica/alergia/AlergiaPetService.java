package br.com.clyvovet.server.fichaclinica.alergia;

import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.pet.PetService;
import br.com.clyvovet.server.tipoalergia.TipoAlergiaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlergiaPetService {

    private final AlergiaPetRepository repository;
    private final PetService petService;
    private final TipoAlergiaService tipoAlergiaService;
    private final ConsultaService consultaService;

    @Transactional(readOnly = true)
    public Page<AlergiaPetResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(AlergiaPetResponse::from);
    }

    @Transactional(readOnly = true)
    public AlergiaPetResponse findById(Long id) {
        return repository.findById(id)
                .map(AlergiaPetResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Alergia do Pet", id));
    }

    @Transactional(readOnly = true)
    public List<AlergiaPetResponse> findByPet(Long petId) {
        return repository.findByPetId(petId).stream().map(AlergiaPetResponse::from).toList();
    }

    @Transactional
    public AlergiaPetResponse create(AlergiaPetRequest request) {
        AlergiaPet a = new AlergiaPet();
        mapRequest(a, request);
        return AlergiaPetResponse.from(repository.save(a));
    }

    @Transactional
    public AlergiaPetResponse update(Long id, AlergiaPetRequest request) {
        AlergiaPet a = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alergia do Pet", id));
        mapRequest(a, request);
        return AlergiaPetResponse.from(repository.save(a));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Alergia do Pet", id);
        repository.deleteById(id);
    }

    private void mapRequest(AlergiaPet a, AlergiaPetRequest r) {
        a.setPet(petService.findEntityById(r.petId()));
        a.setTipoAlergia(tipoAlergiaService.findEntityById(r.tipoAlergiaId()));
        a.setReacao(r.reacao());
        a.setSeveridade(r.severidade());
        a.setDtIdentificacao(r.dtIdentificacao());
        a.setConsultaOrigem(r.consultaOrigemId() != null ? consultaService.findEntityById(r.consultaOrigemId()) : null);
    }
}
