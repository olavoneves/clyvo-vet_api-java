package br.com.clyvovet.server.auditoria;

import br.com.clyvovet.server.enums.AmbienteLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/logs-erro")
@RequiredArgsConstructor
@Tag(name = "Auditoria", description = "Consulta de logs de erro internos")
public class LogErroController {

    private final LogErroService service;

    @GetMapping
    @Operation(summary = "Listar logs de erro paginados")
    @ApiResponse(responseCode = "200", description = "OK")
    public Page<LogErroResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar log por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public LogErroResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/ambiente/{ambiente}")
    @Operation(summary = "Filtrar logs por ambiente (DEV, HOM, PRD)")
    public Page<LogErroResponse> findByAmbiente(@PathVariable AmbienteLog ambiente, Pageable pageable) {
        return service.findByAmbiente(ambiente, pageable);
    }
}
