package br.com.clyvovet.server.veterinario;

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
@RequestMapping("/veterinarios")
@RequiredArgsConstructor
@Tag(name = "Veterinários", description = "Gerenciamento de veterinários")
public class VeterinarioController {

    private final VeterinarioService service;

    @GetMapping
    @Operation(summary = "Listar veterinários paginados")
    public Page<VeterinarioResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar veterinário por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public VeterinarioResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/clinica/{clinicaId}")
    @Operation(summary = "Listar veterinários por clínica")
    public Page<VeterinarioResponse> findByClinica(@PathVariable Long clinicaId, Pageable pageable) {
        return service.findByClinica(clinicaId, pageable);
    }

    @PostMapping
    @Operation(summary = "Criar veterinário")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<VeterinarioResponse> create(@Valid @RequestBody VeterinarioRequest request) {
        VeterinarioResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar veterinário")
    public VeterinarioResponse update(@PathVariable Long id, @Valid @RequestBody VeterinarioRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir veterinário")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
