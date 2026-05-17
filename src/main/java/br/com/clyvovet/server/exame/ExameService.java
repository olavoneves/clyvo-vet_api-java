package br.com.clyvovet.server.exame;

import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.veterinario.VeterinarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExameService {

    private final ExameRepository repository;
    private final ConsultaService consultaService;
    private final VeterinarioService veterinarioService;

    @Transactional(readOnly = true)
    public Page<ExameResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(ExameResponse::from);
    }

    @Transactional(readOnly = true)
    public ExameResponse findById(Long id) {
        return repository.findById(id)
                .map(ExameResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Exame", id));
    }

    @Transactional(readOnly = true)
    public List<ExameResponse> findByConsulta(Long consultaId) {
        return repository.findByConsultaId(consultaId).stream().map(ExameResponse::from).toList();
    }

    @Transactional
    public ExameResponse create(ExameRequest request) {
        Exame e = new Exame();
        mapRequest(e, request);
        return ExameResponse.from(repository.save(e));
    }

    @Transactional
    public ExameResponse update(Long id, ExameRequest request) {
        Exame e = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Exame", id));
        mapRequest(e, request);
        return ExameResponse.from(repository.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Exame", id);
        repository.deleteById(id);
    }

    private void mapRequest(Exame e, ExameRequest r) {
        e.setNome(r.nome());
        e.setDtRealizacao(r.dtRealizacao());
        e.setResultado(r.resultado());
        e.setConsulta(consultaService.findEntityById(r.consultaId()));
        e.setVetSolicitante(r.vetSolicitanteId() != null
                ? veterinarioService.findEntityById(r.vetSolicitanteId()) : null);
    }
}
