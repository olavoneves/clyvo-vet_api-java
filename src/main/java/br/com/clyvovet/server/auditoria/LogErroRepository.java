package br.com.clyvovet.server.auditoria;

import br.com.clyvovet.server.enums.AmbienteLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogErroRepository extends JpaRepository<LogErro, Long> {

    Page<LogErro> findByAmbiente(AmbienteLog ambiente, Pageable pageable);
}
