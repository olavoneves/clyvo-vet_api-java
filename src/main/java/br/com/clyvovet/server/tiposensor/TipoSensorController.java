package br.com.clyvovet.server.tiposensor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/tipos-sensor")
@RequiredArgsConstructor
@Tag(name = "Tipos de Sensor IoT", description = "Gerenciamento de tipos de sensor IoT")
public class TipoSensorController {

    private final TipoSensorService service;

    @GetMapping
    @Operation(summary = "Listar tipos de sensor paginados")
    public Page<TipoSensorResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar tipo de sensor por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public TipoSensorResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar tipo de sensor")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<TipoSensorResponse> create(@Valid @RequestBody TipoSensorRequest request) {
        TipoSensorResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar tipo de sensor")
    public TipoSensorResponse update(@PathVariable Long id, @Valid @RequestBody TipoSensorRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir tipo de sensor")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
