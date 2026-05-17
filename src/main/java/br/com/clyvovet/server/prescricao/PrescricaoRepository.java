package br.com.clyvovet.server.prescricao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescricaoRepository extends JpaRepository<Prescricao, Long> {

    List<Prescricao> findByConsultaId(Long consultaId);
}
