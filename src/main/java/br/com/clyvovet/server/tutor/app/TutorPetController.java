package br.com.clyvovet.server.tutor.app;

import br.com.clyvovet.server.consulta.ConsultaResponse;
import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.pet.PetFichaTecnicaResponse;
import br.com.clyvovet.server.pet.PetResponse;
import br.com.clyvovet.server.pet.PetService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Os pets do tutor autenticado, e nenhum outro.
 *
 * <p>O controller e fino de proposito: deriva o dono do token, confere a posse
 * e chama o mesmo {@code PetService} que a API de gestao chama. Nenhuma regra
 * nova mora aqui — inclusive o cadastro continua disparando o motor de
 * protocolo pelo caminho que ja disparava, porque e o mesmo caminho.
 *
 * <p>Nao ha {@code /pets/tutor/{id}}: era assim que a API de gestao deixava um
 * tutor ler os animais de outro, bastando trocar o numero na URL. Aqui a lista
 * nao tem parametro de dono.
 */
@RestController
@RequestMapping("/tutor/pets")
@RequiredArgsConstructor
@Tag(name = "Tutor: pets", description = "Os pets do tutor autenticado, no aplicativo")
public class TutorPetController {

    private final PosseDoTutor posse;
    private final PetService petService;
    private final ConsultaService consultaService;
    private final AplicacaoVacinaService vacinaService;

    @GetMapping
    @Operation(summary = "Listar os pets do tutor autenticado",
            description = "Paginado. O tutor sai do token; não há filtro por dono.")
    public Page<PetResponse> listar(Pageable pageable) {
        return petService.findAtivosDoTutor(posse.id(), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar um pet do tutor autenticado")
    @ApiResponse(responseCode = "404", description = "O pet não existe ou não é deste tutor")
    public PetResponse buscar(@PathVariable Long id) {
        posse.exigirPet(id);
        return petService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Cadastrar um pet",
            description = "O vínculo com o tutor vem do token: o corpo não tem tutorId.")
    @ApiResponse(responseCode = "201", description = "Criado")
    public ResponseEntity<PetResponse> criar(@Valid @RequestBody PetDoTutorRequest request) {
        PetResponse response = petService.create(request.comTutor(posse.id()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * O tutor do PUT tambem vem do token, e nao do recurso.
     *
     * <p>Parece redundante depois do {@code exigirPet}, e nao e: e o que impede
     * que uma atualizacao mova o pet para outro dono. Sem isso o campo voltaria
     * pela porta dos fundos.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar um pet do tutor autenticado",
            description = "O pet continua vinculado ao tutor do token.")
    @ApiResponse(responseCode = "404", description = "O pet não existe ou não é deste tutor")
    public PetResponse atualizar(@PathVariable Long id,
                                 @Valid @RequestBody PetDoTutorRequest request) {
        posse.exigirPet(id);
        return petService.update(id, request.comTutor(posse.id()));
    }

    /**
     * Remover aqui e inativar, e nao apagar.
     *
     * <p>Esta rota fazia remocao fisica, herdada do {@code PetService}. O que
     * ela nao fazia era perder historico: sem ON DELETE CASCADE em nenhuma das
     * sete tabelas filhas, o Oracle recusava. O que ela fazia era pior de outro
     * jeito — so funcionava em pet sem consulta, sem vacina e sem obrigacao, e
     * devolvia 409 em qualquer pet real.
     *
     * <p>O verbo continua DELETE e a resposta continua 204: para o aplicativo,
     * nada muda. O pet sai das listagens do tutor e continua inteiro para a
     * clinica — inclusive as obrigacoes dele, que sustentam a coorte do painel.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Remover um pet do aplicativo",
            description = "Inativa o pet: ele sai das listagens do tutor e o histórico permanece.")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    @ApiResponse(responseCode = "404", description = "O pet não existe ou não é deste tutor")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        posse.exigirPet(id);
        petService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- sub-recursos de leitura ----------

    @GetMapping("/{id}/ficha-tecnica")
    @Operation(summary = "Ficha técnica do pet (tutor, raça, espécie)")
    @ApiResponse(responseCode = "404", description = "O pet não existe ou não é deste tutor")
    public PetFichaTecnicaResponse fichaTecnica(@PathVariable Long id) {
        posse.exigirPet(id);
        return petService.getFichaTecnica(id);
    }

    @GetMapping("/{id}/consultas")
    @Operation(summary = "Consultas do pet")
    @ApiResponse(responseCode = "404", description = "O pet não existe ou não é deste tutor")
    public Page<ConsultaResponse> consultas(@PathVariable Long id, Pageable pageable) {
        posse.exigirPet(id);
        return consultaService.findByPet(id, pageable);
    }

    @GetMapping("/{id}/vacinas")
    @Operation(summary = "Carteira de vacinação do pet, com o próximo reforço calculado")
    @ApiResponse(responseCode = "404", description = "O pet não existe ou não é deste tutor")
    public Page<AplicacaoVacinaResponse> vacinas(@PathVariable Long id, Pageable pageable) {
        posse.exigirPet(id);
        return vacinaService.findByPet(id, pageable);
    }
}
