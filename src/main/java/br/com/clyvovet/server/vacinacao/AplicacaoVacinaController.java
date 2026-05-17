package br.com.clyvovet.server.vacinacao;

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
@RequestMapping("/vacinas")
@RequiredArgsConstructor
@Tag(name = "Vacinação", description = "Gerenciamento de aplicações de vacinas")
public class AplicacaoVacinaController {

    private final AplicacaoVacinaService service;

    @GetMapping
    @Operation(summary = "Listar aplicações de vacina paginadas")
    public Page<AplicacaoVacinaResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar aplicação de vacina por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public AplicacaoVacinaResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Registrar aplicação de vacina")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<AplicacaoVacinaResponse> create(@Valid @RequestBody AplicacaoVacinaRequest request) {
        AplicacaoVacinaResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar aplicação de vacina")
    public AplicacaoVacinaResponse update(@PathVariable Long id, @Valid @RequestBody AplicacaoVacinaRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir aplicação de vacina")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
