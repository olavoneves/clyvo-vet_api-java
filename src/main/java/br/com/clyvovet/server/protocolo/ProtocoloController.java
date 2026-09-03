package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.ProtocoloCategoria;
import br.com.clyvovet.server.enums.ProtocoloEspecie;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/protocolos")
@RequiredArgsConstructor
@Tag(name = "Protocolos", description = "Catálogo de protocolos clínicos e suas versões")
public class ProtocoloController {

    private final ProtocoloService service;

    @GetMapping
    @Operation(summary = "Listar protocolos ativos da clínica e os globais")
    public Page<ProtocoloResponse> buscar(
            @RequestParam(required = false) ProtocoloCategoria categoria,
            @RequestParam(required = false) ProtocoloEspecie especie,
            Pageable pageable) {
        return service.buscar(categoria, especie, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Protocolo com versões e etapas")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public ProtocoloDetalheResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
