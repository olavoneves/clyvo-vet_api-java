package br.com.clyvovet.server.protocolo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EtapaProtocoloRepository extends JpaRepository<EtapaProtocolo, Long> {

    List<EtapaProtocolo> findByVersaoProtocoloIdOrderByNrOrdemAsc(Long versaoProtocoloId);
}
