package br.com.clyvovet.server.exame;

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
@RequestMapping("/exames")
@RequiredArgsConstructor
@Tag(name = "Exames", description = "Gerenciamento de exames clínicos")
public class ExameController {

    private final ExameService service;

    @GetMapping
    @Operation(summary = "Listar exames paginados")
    public Page<ExameResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar exame por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public ExameResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar exame")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<ExameResponse> create(@Valid @RequestBody ExameRequest request) {
        ExameResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar exame")
    public ExameResponse update(@PathVariable Long id, @Valid @RequestBody ExameRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir exame")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
