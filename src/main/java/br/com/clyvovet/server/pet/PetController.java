package br.com.clyvovet.server.pet;

import br.com.clyvovet.server.consulta.ConsultaResponse;
import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.enums.PetStatus;
import br.com.clyvovet.server.fichaclinica.alergia.AlergiaPetResponse;
import br.com.clyvovet.server.fichaclinica.alergia.AlergiaPetService;
import br.com.clyvovet.server.fichaclinica.condicao.CondicaoPetResponse;
import br.com.clyvovet.server.fichaclinica.condicao.CondicaoPetService;
import br.com.clyvovet.server.iot.alerta.AlertaIotResponse;
import br.com.clyvovet.server.iot.alerta.AlertaIotService;
import br.com.clyvovet.server.vacinacao.AplicacaoVacinaResponse;
import br.com.clyvovet.server.vacinacao.AplicacaoVacinaService;
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
@RequestMapping("/pets")
@RequiredArgsConstructor
@Tag(name = "Pets", description = "Gerenciamento de pets e ficha clínica")
public class PetController {

    private final PetService petService;
    private final ConsultaService consultaService;
    private final AplicacaoVacinaService vacinaService;
    private final AlertaIotService alertaIotService;
    private final CondicaoPetService condicaoPetService;
    private final AlergiaPetService alergiaPetService;

    @GetMapping
    @Operation(summary = "Listar pets paginados")
    public Page<PetResponse> findAll(Pageable pageable) {
        return petService.findAll(pageable);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Filtrar pets por status")
    public Page<PetResponse> findByStatus(@PathVariable PetStatus status, Pageable pageable) {
        return petService.findByStatus(status, pageable);
    }

    @GetMapping("/tutor/{tutorId}")
    @Operation(summary = "Listar pets por tutor")
    public Page<PetResponse> findByTutor(@PathVariable Long tutorId, Pageable pageable) {
        return petService.findByTutor(tutorId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pet por ID")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    public PetResponse findById(@PathVariable Long id) {
        return petService.findById(id);
    }

    @GetMapping("/{id}/ficha-tecnica")
    @Operation(summary = "Ficha técnica completa do pet (tutor, raça, espécie)")
    @ApiResponse(responseCode = "404", description = "Pet não encontrado")
    public PetFichaTecnicaResponse getFichaTecnica(@PathVariable Long id) {
        return petService.getFichaTecnica(id);
    }

    @GetMapping("/{id}/consultas")
    @Operation(summary = "Últimas consultas do pet")
    public Page<ConsultaResponse> getConsultas(@PathVariable Long id, Pageable pageable) {
        return consultaService.findByPet(id, pageable);
    }

    @GetMapping("/{id}/vacinas")
    @Operation(summary = "Histórico de vacinação com próximo reforço calculado")
    public Page<AplicacaoVacinaResponse> getVacinas(@PathVariable Long id, Pageable pageable) {
        return vacinaService.findByPet(id, pageable);
    }

    @GetMapping("/{id}/alertas-iot")
    @Operation(summary = "Alertas IoT não resolvidos do pet")
    public Page<AlertaIotResponse> getAlertasIot(@PathVariable Long id, Pageable pageable) {
        return alertaIotService.findUnresolvedByPet(id, pageable);
    }

    @GetMapping("/{id}/condicoes")
    @Operation(summary = "Condições médicas ativas do pet")
    public List<CondicaoPetResponse> getCondicoes(@PathVariable Long id) {
        return condicaoPetService.findAtivasByPet(id);
    }

    @GetMapping("/{id}/alergias")
    @Operation(summary = "Alergias do pet com severidade")
    public List<AlergiaPetResponse> getAlergias(@PathVariable Long id) {
        return alergiaPetService.findByPet(id);
    }

    @PostMapping
    @Operation(summary = "Criar pet")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<PetResponse> create(@Valid @RequestBody PetRequest request) {
        PetResponse response = petService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar pet")
    public PetResponse update(@PathVariable Long id, @Valid @RequestBody PetRequest request) {
        return petService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir pet")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        petService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
