package br.com.clyvovet.server.fichaclinica.condicao;

import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.pet.PetService;
import br.com.clyvovet.server.tipocondicao.TipoCondicaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CondicaoPetService {

    private final CondicaoPetRepository repository;
    private final PetService petService;
    private final TipoCondicaoService tipoCondicaoService;
    private final ConsultaService consultaService;

    @Transactional(readOnly = true)
    public Page<CondicaoPetResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(CondicaoPetResponse::from);
    }

    @Transactional(readOnly = true)
    public CondicaoPetResponse findById(Long id) {
        return repository.findById(id)
                .map(CondicaoPetResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Condição do Pet", id));
    }

    @Transactional(readOnly = true)
    public List<CondicaoPetResponse> findAtivasByPet(Long petId) {
        return repository.findByPetIdAndAtivoTrue(petId).stream()
                .map(CondicaoPetResponse::from).toList();
    }

    @Transactional
    public CondicaoPetResponse create(CondicaoPetRequest request) {
        CondicaoPet cp = new CondicaoPet();
        mapRequest(cp, request);
        return CondicaoPetResponse.from(repository.save(cp));
    }

    @Transactional
    public CondicaoPetResponse update(Long id, CondicaoPetRequest request) {
        CondicaoPet cp = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Condição do Pet", id));
        mapRequest(cp, request);
        return CondicaoPetResponse.from(repository.save(cp));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Condição do Pet", id);
        repository.deleteById(id);
    }

    private void mapRequest(CondicaoPet cp, CondicaoPetRequest r) {
        cp.setPet(petService.findEntityById(r.petId()));
        cp.setTipoCondicao(tipoCondicaoService.findEntityById(r.tipoCondicaoId()));
        cp.setDtDiagnostico(r.dtDiagnostico());
        cp.setObservacao(r.observacao());
        cp.setAtivo(r.ativo() != null ? r.ativo() : true);
        cp.setConsultaOrigem(r.consultaOrigemId() != null ? consultaService.findEntityById(r.consultaOrigemId()) : null);
    }
}
