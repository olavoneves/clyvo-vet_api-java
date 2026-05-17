package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
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
public class AgendamentoService {

    private final AgendamentoRepository repository;
    private final PetService petService;
    private final VeterinarioService veterinarioService;

    @Transactional(readOnly = true)
    public Page<AgendamentoResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(AgendamentoResponse::from);
    }

    @Transactional(readOnly = true)
    public AgendamentoResponse findById(Long id) {
        return repository.findById(id)
                .map(AgendamentoResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Agendamento", id));
    }

    @Transactional(readOnly = true)
    public Page<AgendamentoResponse> findByStatus(AgendamentoStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(AgendamentoResponse::from);
    }

    @Transactional
    public AgendamentoResponse create(AgendamentoRequest request) {
        Agendamento a = new Agendamento();
        mapRequest(a, request);
        return AgendamentoResponse.from(repository.save(a));
    }

    @Transactional
    public AgendamentoResponse update(Long id, AgendamentoRequest request) {
        Agendamento a = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Agendamento", id));
        mapRequest(a, request);
        return AgendamentoResponse.from(repository.save(a));
    }

    @Transactional
    public AgendamentoResponse updateStatus(Long id, AgendamentoStatusRequest request) {
        Agendamento a = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Agendamento", id));
        a.setStatus(request.status());
        if (request.motivoCancelamento() != null) {
            a.setMotivoCancelamento(request.motivoCancelamento());
        }
        return AgendamentoResponse.from(repository.save(a));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Agendamento", id);
        repository.deleteById(id);
    }

    private void mapRequest(Agendamento a, AgendamentoRequest r) {
        a.setDtAgendamento(r.dtAgendamento());
        a.setHrAgendamento(r.hrAgendamento());
        a.setStatus(r.status());
        a.setCanalOrigem(r.canalOrigem());
        a.setMotivoCancelamento(r.motivoCancelamento());
        a.setPet(petService.findEntityById(r.petId()));
        a.setVeterinario(veterinarioService.findEntityById(r.veterinarioId()));
        a.setIdConsulta(r.consultaId());
    }
}
