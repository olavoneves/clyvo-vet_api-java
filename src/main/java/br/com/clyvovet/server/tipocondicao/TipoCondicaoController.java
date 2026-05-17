package br.com.clyvovet.server.tipocondicao;

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
@RequestMapping("/tipos-condicao")
@RequiredArgsConstructor
@Tag(name = "Tipos de Condição", description = "Gerenciamento de tipos de condição clínica")
public class TipoCondicaoController {

    private final TipoCondicaoService service;

    @GetMapping
    @Operation(summary = "Listar tipos de condição paginados")
    public Page<TipoCondicaoResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar tipo de condição por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public TipoCondicaoResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar tipo de condição")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<TipoCondicaoResponse> create(@Valid @RequestBody TipoCondicaoRequest request) {
        TipoCondicaoResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar tipo de condição")
    public TipoCondicaoResponse update(@PathVariable Long id, @Valid @RequestBody TipoCondicaoRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir tipo de condição")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
