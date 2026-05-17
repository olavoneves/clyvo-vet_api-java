package br.com.clyvovet.server.exame;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExameRepository extends JpaRepository<Exame, Long> {

    List<Exame> findByConsultaId(Long consultaId);
}
