package br.com.clyvovet.server.clinica;

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
@RequestMapping("/clinicas")
@RequiredArgsConstructor
@Tag(name = "Clínicas", description = "Gerenciamento de clínicas veterinárias")
public class ClinicaController {

    private final ClinicaService service;

    @GetMapping
    @Operation(summary = "Listar clínicas paginadas")
    public Page<ClinicaResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    /**
     * Aberta de proposito: e o unico jeito de a tela de cadastro do aplicativo
     * descobrir o clinicaId que {@code POST /tutores} exige.
     */
    @GetMapping("/publicas")
    @Operation(summary = "Listar clínicas para a tela de cadastro (sem autenticação)",
            description = "Projeção reduzida: id, nome e cidade. Não expõe CNPJ nem contato.")
    public Page<ClinicaPublicaResponse> findAllPublicas(Pageable pageable) {
        return service.findAllPublicas(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar clínica por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public ClinicaResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Criar clínica")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<ClinicaResponse> create(@Valid @RequestBody ClinicaRequest request) {
        ClinicaResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar clínica")
    public ClinicaResponse update(@PathVariable Long id, @Valid @RequestBody ClinicaRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir clínica")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
