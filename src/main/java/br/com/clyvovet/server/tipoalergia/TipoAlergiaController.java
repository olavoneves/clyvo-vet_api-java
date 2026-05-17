package br.com.clyvovet.server.tipoalergia;

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
@RequestMapping("/tipos-alergia")
@RequiredArgsConstructor
@Tag(name = "Tipos de Alergia", description = "Gerenciamento de tipos de alergia")
public class TipoAlergiaController {

    private final TipoAlergiaService service;

    @GetMapping
    @Operation(summary = "Listar tipos de alergia paginados")
    public Page<TipoAlergiaResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar tipo de alergia por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public TipoAlergiaResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar tipo de alergia")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<TipoAlergiaResponse> create(@Valid @RequestBody TipoAlergiaRequest request) {
        TipoAlergiaResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar tipo de alergia")
    public TipoAlergiaResponse update(@PathVariable Long id, @Valid @RequestBody TipoAlergiaRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir tipo de alergia")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
