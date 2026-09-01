package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.ProtocoloCategoria;
import br.com.clyvovet.server.enums.ProtocoloEspecie;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProtocoloService {

    private final ProtocoloRepository repository;
    private final VersaoProtocoloRepository versaoRepository;
    private final EtapaProtocoloRepository etapaRepository;

    @Transactional(readOnly = true)
    public Page<ProtocoloResponse> buscar(ProtocoloCategoria categoria, ProtocoloEspecie especie,
                                          Pageable pageable) {
        if (categoria != null) {
            return repository.findByDsCategoriaAndFlAtivoTrue(categoria, pageable)
                    .map(ProtocoloResponse::from);
        }
        if (especie != null) {
            return repository.findByDsEspecieAndFlAtivoTrue(especie, pageable)
                    .map(ProtocoloResponse::from);
        }
        return repository.findByFlAtivoTrue(pageable).map(ProtocoloResponse::from);
    }

    @Transactional(readOnly = true)
    public ProtocoloDetalheResponse findById(Long id) {
        Protocolo protocolo = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Protocolo", id));

        var versoes = versaoRepository.findByProtocoloIdOrderByNrVersaoDesc(id).stream()
                .map(v -> VersaoProtocoloResponse.from(
                        v, etapaRepository.findByVersaoProtocoloIdOrderByNrOrdemAsc(v.getId())))
                .toList();

        return new ProtocoloDetalheResponse(ProtocoloResponse.from(protocolo), versoes);
    }
}
