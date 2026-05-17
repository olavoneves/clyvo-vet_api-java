package br.com.clyvovet.server.fichaclinica.condicao;

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
@RequestMapping("/condicoes-pet")
@RequiredArgsConstructor
@Tag(name = "Condições do Pet", description = "Gerenciamento de condições médicas dos pets")
public class CondicaoPetController {

    private final CondicaoPetService service;

    @GetMapping
    @Operation(summary = "Listar condições paginadas")
    public Page<CondicaoPetResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar condição por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public CondicaoPetResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar condição do pet")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<CondicaoPetResponse> create(@Valid @RequestBody CondicaoPetRequest request) {
        CondicaoPetResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar condição do pet")
    public CondicaoPetResponse update(@PathVariable Long id, @Valid @RequestBody CondicaoPetRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir condição do pet")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
