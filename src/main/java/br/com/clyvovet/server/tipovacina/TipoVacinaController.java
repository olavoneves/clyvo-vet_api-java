package br.com.clyvovet.server.tipovacina;

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
@RequestMapping("/tipos-vacina")
@RequiredArgsConstructor
@Tag(name = "Tipos de Vacina", description = "Gerenciamento de tipos de vacina")
public class TipoVacinaController {

    private final TipoVacinaService service;

    @GetMapping
    @Operation(summary = "Listar tipos de vacina paginados")
    public Page<TipoVacinaResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar tipo de vacina por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public TipoVacinaResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar tipo de vacina")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<TipoVacinaResponse> create(@Valid @RequestBody TipoVacinaRequest request) {
        TipoVacinaResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar tipo de vacina")
    public TipoVacinaResponse update(@PathVariable Long id, @Valid @RequestBody TipoVacinaRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir tipo de vacina")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
