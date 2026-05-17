package br.com.clyvovet.server.iot.alerta;

import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.iot.leitura.LeituraIotService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertaIotService {

    private final AlertaIotRepository repository;
    private final LeituraIotService leituraIotService;
    private final ConsultaService consultaService;

    @Transactional(readOnly = true)
    public Page<AlertaIotResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(AlertaIotResponse::from);
    }

    @Transactional(readOnly = true)
    public AlertaIotResponse findById(Long id) {
        return repository.findById(id)
                .map(AlertaIotResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Alerta IoT", id));
    }

    @Transactional(readOnly = true)
    public Page<AlertaIotResponse> findNaoResolvidos(Pageable pageable) {
        return repository.findByResolvido(false, pageable).map(AlertaIotResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AlertaIotResponse> findUnresolvedByPet(Long petId, Pageable pageable) {
        return repository.findUnresolvedByPetId(petId, pageable).map(AlertaIotResponse::from);
    }

    @Transactional
    public AlertaIotResponse create(AlertaIotRequest request) {
        AlertaIot a = new AlertaIot();
        mapRequest(a, request);
        return AlertaIotResponse.from(repository.save(a));
    }

    @Transactional
    public AlertaIotResponse update(Long id, AlertaIotRequest request) {
        AlertaIot a = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alerta IoT", id));
        mapRequest(a, request);
        return AlertaIotResponse.from(repository.save(a));
    }

    @Transactional
    public AlertaIotResponse resolver(Long id) {
        AlertaIot a = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alerta IoT", id));
        a.setResolvido(true);
        return AlertaIotResponse.from(repository.save(a));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Alerta IoT", id);
        repository.deleteById(id);
    }

    private void mapRequest(AlertaIot a, AlertaIotRequest r) {
        a.setLeitura(leituraIotService.findEntityById(r.leituraId()));
        a.setDescricao(r.descricao());
        a.setSeveridade(r.severidade());
        a.setDtAlerta(r.dtAlerta());
        a.setResolvido(r.resolvido() != null ? r.resolvido() : false);
        a.setConsulta(r.consultaId() != null ? consultaService.findEntityById(r.consultaId()) : null);
    }
}
