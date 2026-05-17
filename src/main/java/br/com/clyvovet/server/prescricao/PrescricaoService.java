package br.com.clyvovet.server.prescricao;

import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.medicamento.MedicamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrescricaoService {

    private final PrescricaoRepository repository;
    private final ConsultaService consultaService;
    private final MedicamentoService medicamentoService;

    @Transactional(readOnly = true)
    public Page<PrescricaoResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(PrescricaoResponse::from);
    }

    @Transactional(readOnly = true)
    public PrescricaoResponse findById(Long id) {
        return repository.findById(id)
                .map(PrescricaoResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Prescrição", id));
    }

    @Transactional(readOnly = true)
    public List<PrescricaoResponse> findByConsulta(Long consultaId) {
        return repository.findByConsultaId(consultaId).stream()
                .map(PrescricaoResponse::from).toList();
    }

    @Transactional
    public PrescricaoResponse create(PrescricaoRequest request) {
        Prescricao p = new Prescricao();
        mapRequest(p, request);
        return PrescricaoResponse.from(repository.save(p));
    }

    @Transactional
    public PrescricaoResponse update(Long id, PrescricaoRequest request) {
        Prescricao p = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prescrição", id));
        mapRequest(p, request);
        return PrescricaoResponse.from(repository.save(p));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Prescrição", id);
        repository.deleteById(id);
    }

    private void mapRequest(Prescricao p, PrescricaoRequest r) {
        p.setConsulta(consultaService.findEntityById(r.consultaId()));
        p.setMedicamento(medicamentoService.findEntityById(r.medicamentoId()));
        p.setDosagem(r.dosagem());
        p.setFrequencia(r.frequencia());
        p.setDuracaoDias(r.duracaoDias());
    }
}
