package br.com.clyvovet.server.vacinacao;

import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.pet.PetService;
import br.com.clyvovet.server.tipovacina.TipoVacinaService;
import br.com.clyvovet.server.veterinario.VeterinarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AplicacaoVacinaService {

    private final AplicacaoVacinaRepository repository;
    private final PetService petService;
    private final TipoVacinaService tipoVacinaService;
    private final VeterinarioService veterinarioService;
    private final ConsultaService consultaService;

    @Transactional(readOnly = true)
    public Page<AplicacaoVacinaResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(AplicacaoVacinaResponse::from);
    }

    @Transactional(readOnly = true)
    public AplicacaoVacinaResponse findById(Long id) {
        return repository.findById(id)
                .map(AplicacaoVacinaResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Aplicação de Vacina", id));
    }

    @Transactional(readOnly = true)
    public Page<AplicacaoVacinaResponse> findByPet(Long petId, Pageable pageable) {
        return repository.findByPetIdWithTipoVacina(petId, pageable).map(AplicacaoVacinaResponse::from);
    }

    @Transactional
    public AplicacaoVacinaResponse create(AplicacaoVacinaRequest request) {
        AplicacaoVacina av = new AplicacaoVacina();
        mapRequest(av, request);
        return AplicacaoVacinaResponse.from(repository.save(av));
    }

    @Transactional
    public AplicacaoVacinaResponse update(Long id, AplicacaoVacinaRequest request) {
        AplicacaoVacina av = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aplicação de Vacina", id));
        mapRequest(av, request);
        return AplicacaoVacinaResponse.from(repository.save(av));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Aplicação de Vacina", id);
        repository.deleteById(id);
    }

    private void mapRequest(AplicacaoVacina av, AplicacaoVacinaRequest r) {
        av.setPet(petService.findEntityById(r.petId()));
        av.setTipoVacina(tipoVacinaService.findEntityById(r.tipoVacinaId()));
        av.setVeterinario(veterinarioService.findEntityById(r.veterinarioId()));
        av.setConsulta(r.consultaId() != null ? consultaService.findEntityById(r.consultaId()) : null);
        av.setDtAplicacao(r.dtAplicacao());
        av.setNumeroDose(r.numeroDose());
        av.setLote(r.lote());
    }
}
