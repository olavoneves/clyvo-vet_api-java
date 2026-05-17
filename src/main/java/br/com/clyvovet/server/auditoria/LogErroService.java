package br.com.clyvovet.server.auditoria;

import br.com.clyvovet.server.enums.AmbienteLog;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogErroService {

    private final LogErroRepository repository;

    @Transactional(readOnly = true)
    public Page<LogErroResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(LogErroResponse::from);
    }

    @Transactional(readOnly = true)
    public LogErroResponse findById(Long id) {
        return repository.findById(id)
                .map(LogErroResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Log de Erro", id));
    }

    @Transactional(readOnly = true)
    public Page<LogErroResponse> findByAmbiente(AmbienteLog ambiente, Pageable pageable) {
        return repository.findByAmbiente(ambiente, pageable).map(LogErroResponse::from);
    }
}
