package br.com.clyvovet.server.anamnese;

import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnamneseService {

    private final AnamneseRepository repository;
    private final ConsultaService consultaService;

    @Transactional(readOnly = true)
    public Page<AnamneseResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(AnamneseResponse::from);
    }

    @Transactional(readOnly = true)
    public AnamneseResponse findById(Long id) {
        return repository.findById(id)
                .map(AnamneseResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Anamnese", id));
    }

    @Transactional(readOnly = true)
    public AnamneseResponse findByConsulta(Long consultaId) {
        return repository.findByConsultaId(consultaId)
                .map(AnamneseResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Anamnese para consulta " + consultaId + " não encontrada"));
    }

    @Transactional
    public AnamneseResponse create(AnamneseRequest request) {
        Anamnese a = new Anamnese();
        mapRequest(a, request);
        return AnamneseResponse.from(repository.save(a));
    }

    @Transactional
    public AnamneseResponse update(Long id, AnamneseRequest request) {
        Anamnese a = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Anamnese", id));
        mapRequest(a, request);
        return AnamneseResponse.from(repository.save(a));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Anamnese", id);
        repository.deleteById(id);
    }

    private void mapRequest(Anamnese a, AnamneseRequest r) {
        a.setConsulta(consultaService.findEntityById(r.consultaId()));
        a.setQueixaPrincipal(r.queixaPrincipal());
        a.setHistoricoRelatado(r.historicoRelatado());
        a.setPesoKg(r.pesoKg());
        a.setTemperaturaC(r.temperaturaC());
        a.setFreqCardiaca(r.freqCardiaca());
        a.setFreqRespiratoria(r.freqRespiratoria());
        a.setCondicaoCorporal(r.condicaoCorporal());
        a.setMucosas(r.mucosas());
        a.setLinfonodos(r.linfonodos());
        a.setObservacoesClinicas(r.observacoesClinicas());
    }
}
