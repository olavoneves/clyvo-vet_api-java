package br.com.clyvovet.server.iot.sensor;

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
@RequestMapping("/sensores-iot")
@RequiredArgsConstructor
@Tag(name = "Sensores IoT", description = "Gerenciamento de sensores IoT dos pets")
public class SensorIotController {

    private final SensorIotService service;

    @GetMapping
    @Operation(summary = "Listar sensores paginados")
    public Page<SensorIotResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar sensor por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public SensorIotResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/pet/{petId}")
    @Operation(summary = "Listar sensores por pet")
    public Page<SensorIotResponse> findByPet(@PathVariable Long petId, Pageable pageable) {
        return service.findByPet(petId, pageable);
    }

    @PostMapping
    @Operation(summary = "Instalar sensor IoT")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<SensorIotResponse> create(@Valid @RequestBody SensorIotRequest request) {
        SensorIotResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar sensor IoT")
    public SensorIotResponse update(@PathVariable Long id, @Valid @RequestBody SensorIotRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover sensor IoT")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
