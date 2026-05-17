package br.com.clyvovet.server.consulta;

import br.com.clyvovet.server.anamnese.AnamneseResponse;
import br.com.clyvovet.server.anamnese.AnamneseService;
import br.com.clyvovet.server.enums.ConsultaStatus;
import br.com.clyvovet.server.exame.ExameResponse;
import br.com.clyvovet.server.exame.ExameService;
import br.com.clyvovet.server.prescricao.PrescricaoResponse;
import br.com.clyvovet.server.prescricao.PrescricaoService;
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
import java.util.List;

@RestController
@RequestMapping("/consultas")
@RequiredArgsConstructor
@Tag(name = "Consultas", description = "Gerenciamento de consultas veterinárias")
public class ConsultaController {

    private final ConsultaService service;
    private final AnamneseService anamneseService;
    private final PrescricaoService prescricaoService;
    private final ExameService exameService;

    @GetMapping
    @Operation(summary = "Listar consultas paginadas")
    public Page<ConsultaResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Filtrar consultas por status")
    public Page<ConsultaResponse> findByStatus(@PathVariable ConsultaStatus status, Pageable pageable) {
        return service.findByStatus(status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar consulta por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public ConsultaResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/anamnese")
    @Operation(summary = "Buscar anamnese da consulta")
    @ApiResponse(responseCode = "404", description = "Consulta ou anamnese não encontrada")
    public AnamneseResponse getAnamnese(@PathVariable Long id) {
        return anamneseService.findByConsulta(id);
    }

    @GetMapping("/{id}/prescricoes")
    @Operation(summary = "Listar prescrições da consulta")
    public List<PrescricaoResponse> getPrescricoes(@PathVariable Long id) {
        return prescricaoService.findByConsulta(id);
    }

    @GetMapping("/{id}/exames")
    @Operation(summary = "Listar exames da consulta")
    public List<ExameResponse> getExames(@PathVariable Long id) {
        return exameService.findByConsulta(id);
    }

    @PostMapping
    @Operation(summary = "Criar consulta")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<ConsultaResponse> create(@Valid @RequestBody ConsultaRequest request) {
        ConsultaResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar consulta")
    public ConsultaResponse update(@PathVariable Long id, @Valid @RequestBody ConsultaRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir consulta")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
