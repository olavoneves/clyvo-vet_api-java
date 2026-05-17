package br.com.clyvovet.server.especie;

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
@RequestMapping("/especies")
@RequiredArgsConstructor
@Tag(name = "Espécies", description = "Gerenciamento de espécies animais")
public class EspecieController {

    private final EspecieService service;

    @GetMapping
    @Operation(summary = "Listar espécies paginadas")
    @ApiResponse(responseCode = "200", description = "OK")
    public Page<EspecieResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar espécie por ID")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public EspecieResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar espécie")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<EspecieResponse> create(@Valid @RequestBody EspecieRequest request) {
        EspecieResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar espécie")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public EspecieResponse update(@PathVariable Long id, @Valid @RequestBody EspecieRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir espécie")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
