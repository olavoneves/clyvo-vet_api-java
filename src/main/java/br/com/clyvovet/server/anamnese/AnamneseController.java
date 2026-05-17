package br.com.clyvovet.server.anamnese;

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
@RequestMapping("/anamneses")
@RequiredArgsConstructor
@Tag(name = "Anamneses", description = "Gerenciamento de anamneses clínicas")
public class AnamneseController {

    private final AnamneseService service;

    @GetMapping
    @Operation(summary = "Listar anamneses paginadas")
    public Page<AnamneseResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar anamnese por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public AnamneseResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar anamnese")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<AnamneseResponse> create(@Valid @RequestBody AnamneseRequest request) {
        AnamneseResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar anamnese")
    public AnamneseResponse update(@PathVariable Long id, @Valid @RequestBody AnamneseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir anamnese")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
