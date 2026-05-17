package br.com.clyvovet.server.raca;

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
@RequestMapping("/racas")
@RequiredArgsConstructor
@Tag(name = "Raças", description = "Gerenciamento de raças de animais")
public class RacaController {

    private final RacaService service;

    @GetMapping
    @Operation(summary = "Listar raças paginadas")
    public Page<RacaResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar raça por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public RacaResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/especie/{especieId}")
    @Operation(summary = "Listar raças por espécie")
    public Page<RacaResponse> findByEspecie(@PathVariable Long especieId, Pageable pageable) {
        return service.findByEspecie(especieId, pageable);
    }

    @PostMapping
    @Operation(summary = "Criar raça")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<RacaResponse> create(@Valid @RequestBody RacaRequest request) {
        RacaResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar raça")
    public RacaResponse update(@PathVariable Long id, @Valid @RequestBody RacaRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir raça")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
