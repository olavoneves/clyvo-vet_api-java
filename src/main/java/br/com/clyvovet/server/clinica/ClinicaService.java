package br.com.clyvovet.server.clinica;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ClinicaService {

    private final ClinicaRepository repository;

    @Transactional(readOnly = true)
    public Page<ClinicaResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(ClinicaResponse::from);
    }

    /**
     * A lista que a tela de cadastro do aplicativo consome, sem token.
     *
     * <p>Mesma consulta do {@link #findAll}, projecao menor: ver
     * {@link ClinicaPublicaResponse} para o que fica de fora e por que.
     */
    @Transactional(readOnly = true)
    public Page<ClinicaPublicaResponse> findAllPublicas(Pageable pageable) {
        return repository.findAll(pageable).map(ClinicaPublicaResponse::from);
    }

    @Transactional(readOnly = true)
    public ClinicaResponse findById(Long id) {
        return repository.findById(id)
                .map(ClinicaResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Clínica", id));
    }

    public Clinica findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Clínica", id));
    }

    @Transactional
    public ClinicaResponse create(ClinicaRequest request) {
        Clinica c = new Clinica();
        mapRequest(c, request);
        return ClinicaResponse.from(repository.save(c));
    }

    @Transactional
    public ClinicaResponse update(Long id, ClinicaRequest request) {
        Clinica c = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Clínica", id));
        mapRequest(c, request);
        return ClinicaResponse.from(repository.save(c));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Clínica", id);
        repository.deleteById(id);
    }

    private void mapRequest(Clinica c, ClinicaRequest r) {
        c.setNome(r.nome());
        c.setCnpj(r.cnpj());
        c.setLogradouro(r.logradouro());
        c.setNumero(r.numero());
        c.setBairro(r.bairro());
        c.setCidade(r.cidade());
        c.setEstado(r.estado());
        c.setCep(r.cep());
        c.setTelefone(r.telefone());
        // NOT NULL no banco: clinica sem historico entra com zero
        c.setNrTicketMedio(r.nrTicketMedio() != null ? r.nrTicketMedio() : BigDecimal.ZERO);
    }
}
