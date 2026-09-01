package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.VersaoProtocoloStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersaoProtocoloRepository extends JpaRepository<VersaoProtocolo, Long> {

    List<VersaoProtocolo> findByProtocoloIdOrderByNrVersaoDesc(Long protocoloId);

    List<VersaoProtocolo> findByProtocoloIdAndDsStatus(Long protocoloId, VersaoProtocoloStatus status);
}
