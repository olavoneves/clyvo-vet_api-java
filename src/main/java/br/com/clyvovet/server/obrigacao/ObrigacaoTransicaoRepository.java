package br.com.clyvovet.server.obrigacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObrigacaoTransicaoRepository extends JpaRepository<ObrigacaoTransicao, Long> {

    List<ObrigacaoTransicao> findByObrigacaoIdOrderByDtOcorrenciaAsc(Long obrigacaoId);
}
