package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.config.CacheConfig;
import br.com.clyvovet.server.enums.ProtocoloCategoria;
import br.com.clyvovet.server.enums.ProtocoloEspecie;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProtocoloService {

    /**
     * O TenantContext abre toda chave de cache.
     *
     * <p>O filtro do Hibernate recorta a consulta, nao o cache: sem o tenant na
     * chave, a primeira clinica a pedir o protocolo 12 o deixaria guardado para
     * todas as outras — inclusive as que nao podem ve-lo. Protocolo global
     * (id_clinica nulo) e visivel para todas, mas isso e decisao da condicao do
     * filtro, nao um motivo para compartilhar a entrada.
     */
    private static final String TENANT = "T(br.com.clyvovet.server.tenant.TenantContext).getOrSemTenant()";

    private final ProtocoloRepository repository;
    private final VersaoProtocoloRepository versaoRepository;
    private final EtapaProtocoloRepository etapaRepository;

    @Cacheable(cacheNames = CacheConfig.CATALOGO_DE_PROTOCOLOS,
            key = TENANT + " + '|lista|' + #categoria + '|' + #especie + '|' + #pageable")
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

    @Cacheable(cacheNames = CacheConfig.CATALOGO_DE_PROTOCOLOS,
            key = TENANT + " + '|detalhe|' + #id")
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
