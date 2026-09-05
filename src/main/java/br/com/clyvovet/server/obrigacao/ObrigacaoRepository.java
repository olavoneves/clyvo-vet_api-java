package br.com.clyvovet.server.obrigacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ObrigacaoRepository
        extends JpaRepository<Obrigacao, Long>, JpaSpecificationExecutor<Obrigacao> {

    // a listagem sempre mostra pet, etapa e protocolo: sem o grafo, uma pagina
    // de 20 obrigacoes vira 61 selects
    @Override
    @EntityGraph(attributePaths = {"pet", "etapa", "etapa.versaoProtocolo", "etapa.versaoProtocolo.protocolo"})
    Page<Obrigacao> findAll(Specification<Obrigacao> spec, Pageable pageable);

    /** A obrigacao que este agendamento atende, quando ha uma. */
    Optional<Obrigacao> findByAgendamentoId(Long agendamentoId);
}
