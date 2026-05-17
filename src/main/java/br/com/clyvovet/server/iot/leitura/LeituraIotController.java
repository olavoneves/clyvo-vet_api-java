package br.com.clyvovet.server.iot.leitura;

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
@RequestMapping("/leituras-iot")
@RequiredArgsConstructor
@Tag(name = "Leituras IoT", description = "Gerenciamento de leituras de sensores IoT")
public class LeituraIotController {

    private final LeituraIotService service;

    @GetMapping
    @Operation(summary = "Listar leituras paginadas")
    public Page<LeituraIotResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar leitura por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public LeituraIotResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/sensor/{sensorId}")
    @Operation(summary = "Listar leituras por sensor")
    public Page<LeituraIotResponse> findBySensor(@PathVariable Long sensorId, Pageable pageable) {
        return service.findBySensor(sensorId, pageable);
    }

    @PostMapping
    @Operation(summary = "Registrar leitura IoT")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<LeituraIotResponse> create(@Valid @RequestBody LeituraIotRequest request) {
        LeituraIotResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar leitura IoT")
    public LeituraIotResponse update(@PathVariable Long id, @Valid @RequestBody LeituraIotRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir leitura IoT")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
