package br.com.clyvovet.server.iot.alerta;

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
@RequestMapping("/alertas-iot")
@RequiredArgsConstructor
@Tag(name = "Alertas IoT", description = "Gerenciamento de alertas de sensores IoT")
public class AlertaIotController {

    private final AlertaIotService service;

    @GetMapping
    @Operation(summary = "Listar alertas paginados")
    public Page<AlertaIotResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/nao-resolvidos")
    @Operation(summary = "Listar alertas não resolvidos")
    public Page<AlertaIotResponse> findNaoResolvidos(Pageable pageable) {
        return service.findNaoResolvidos(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar alerta por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public AlertaIotResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar alerta IoT")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<AlertaIotResponse> create(@Valid @RequestBody AlertaIotRequest request) {
        AlertaIotResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar alerta IoT")
    public AlertaIotResponse update(@PathVariable Long id, @Valid @RequestBody AlertaIotRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/resolver")
    @Operation(summary = "Marcar alerta como resolvido")
    @ApiResponse(responseCode = "200", description = "Alerta resolvido")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public AlertaIotResponse resolver(@PathVariable Long id) {
        return service.resolver(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir alerta IoT")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
