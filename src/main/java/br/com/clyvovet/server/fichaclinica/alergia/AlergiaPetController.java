package br.com.clyvovet.server.fichaclinica.alergia;

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
@RequestMapping("/alergias-pet")
@RequiredArgsConstructor
@Tag(name = "Alergias do Pet", description = "Gerenciamento de alergias dos pets")
public class AlergiaPetController {

    private final AlergiaPetService service;

    @GetMapping
    @Operation(summary = "Listar alergias paginadas")
    public Page<AlergiaPetResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar alergia por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public AlergiaPetResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Registrar alergia do pet")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<AlergiaPetResponse> create(@Valid @RequestBody AlergiaPetRequest request) {
        AlergiaPetResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar alergia do pet")
    public AlergiaPetResponse update(@PathVariable Long id, @Valid @RequestBody AlergiaPetRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir alergia do pet")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
