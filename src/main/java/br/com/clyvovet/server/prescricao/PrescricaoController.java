package br.com.clyvovet.server.prescricao;

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
@RequestMapping("/prescricoes")
@RequiredArgsConstructor
@Tag(name = "Prescrições", description = "Gerenciamento de prescrições médicas")
public class PrescricaoController {

    private final PrescricaoService service;

    @GetMapping
    @Operation(summary = "Listar prescrições paginadas")
    public Page<PrescricaoResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar prescrição por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public PrescricaoResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar prescrição")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<PrescricaoResponse> create(@Valid @RequestBody PrescricaoRequest request) {
        PrescricaoResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar prescrição")
    public PrescricaoResponse update(@PathVariable Long id, @Valid @RequestBody PrescricaoRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir prescrição")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
