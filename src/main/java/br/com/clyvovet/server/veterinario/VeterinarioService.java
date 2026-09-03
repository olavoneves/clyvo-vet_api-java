package br.com.clyvovet.server.veterinario;

import br.com.clyvovet.server.clinica.ClinicaService;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VeterinarioService {

    private final VeterinarioRepository repository;
    private final ClinicaService clinicaService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<VeterinarioResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(VeterinarioResponse::from);
    }

    @Transactional(readOnly = true)
    public VeterinarioResponse findById(Long id) {
        return repository.findById(id)
                .map(VeterinarioResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Veterinário", id));
    }

    public Veterinario findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Veterinário", id));
    }

    @Transactional(readOnly = true)
    public Page<VeterinarioResponse> findByClinica(Long clinicaId, Pageable pageable) {
        return repository.findByClinicaId(clinicaId, pageable).map(VeterinarioResponse::from);
    }

    @Transactional
    public VeterinarioResponse create(VeterinarioRequest request) {
        Veterinario v = new Veterinario();
        mapRequest(v, request);
        // a clinica vem do token do colaborador, nunca do corpo da requisicao
        v.setClinica(clinicaService.findEntityById(TenantContext.getOrThrow()));
        return VeterinarioResponse.from(repository.save(v));
    }

    @Transactional
    public VeterinarioResponse update(Long id, VeterinarioRequest request) {
        Veterinario v = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Veterinário", id));
        mapRequest(v, request);
        // clinica nao muda no update: transferir veterinario entre clinicas nao
        // e edicao de cadastro, e o filtro de tenant ja barra o cross-clinic
        return VeterinarioResponse.from(repository.save(v));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("Veterinário", id);
        repository.deleteById(id);
    }

    private void mapRequest(Veterinario v, VeterinarioRequest request) {
        v.setNome(request.nome());
        v.setCrmv(request.crmv());
        v.setEspecialidade(request.especialidade());
        v.setEmail(request.email());
        v.setSenhaHash(passwordEncoder.encode(request.senha()));
    }
}
