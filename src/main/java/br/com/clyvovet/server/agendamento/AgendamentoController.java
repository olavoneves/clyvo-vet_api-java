package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
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
@RequestMapping("/agendamentos")
@RequiredArgsConstructor
@Tag(name = "Agendamentos", description = "Gerenciamento de agendamentos de consultas")
public class AgendamentoController {

    private final AgendamentoService service;

    @GetMapping
    @Operation(summary = "Listar agendamentos paginados")
    public Page<AgendamentoResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Filtrar agendamentos por status")
    public Page<AgendamentoResponse> findByStatus(@PathVariable AgendamentoStatus status, Pageable pageable) {
        return service.findByStatus(status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar agendamento por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public AgendamentoResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar agendamento")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<AgendamentoResponse> create(@Valid @RequestBody AgendamentoRequest request) {
        AgendamentoResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar agendamento")
    public AgendamentoResponse update(@PathVariable Long id, @Valid @RequestBody AgendamentoRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Atualizar status do agendamento")
    @ApiResponse(responseCode = "200", description = "Status atualizado")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public AgendamentoResponse updateStatus(@PathVariable Long id, @Valid @RequestBody AgendamentoStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir agendamento")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
