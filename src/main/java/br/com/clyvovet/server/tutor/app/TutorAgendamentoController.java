package br.com.clyvovet.server.tutor.app;

import br.com.clyvovet.server.agendamento.AgendamentoRequest;
import br.com.clyvovet.server.agendamento.AgendamentoResponse;
import br.com.clyvovet.server.agendamento.AgendamentoService;
import br.com.clyvovet.server.agendamento.AgendamentoStatusRequest;
import br.com.clyvovet.server.enums.AgendamentoStatus;
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
 * Os compromissos dos pets do tutor autenticado.
 *
 * <p>Marcar por aqui e um CRUD: o horario vem do aplicativo ja escolhido. Quem
 * conversa sobre o que esta pendente e sugere horario livre e o agente, em
 * {@code /api/agente/**}, e e ele — pelo {@code AgendaService} — que fecha o elo
 * com a obrigacao do protocolo. As duas portas convivem de proposito; esta nao
 * duplica aquela.
 */
@RestController
@RequestMapping("/tutor/agendamentos")
@RequiredArgsConstructor
@Tag(name = "Tutor: agendamentos", description = "Os compromissos dos pets do tutor autenticado")
public class TutorAgendamentoController {

    /** O motivo e sempre este: o aplicativo nao pergunta, e o balcao ja tem o campo livre. */
    private static final String MOTIVO_DO_TUTOR = "Cancelado pelo tutor no aplicativo";

    private final PosseDoTutor posse;
    private final AgendamentoService service;

    @GetMapping
    @Operation(summary = "Listar os compromissos dos pets do tutor autenticado",
            description = "Paginado. O tutor sai do token; não há filtro por dono.")
    public Page<AgendamentoResponse> listar(Pageable pageable) {
        return service.findByTutor(posse.id(), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar um compromisso de um pet do tutor autenticado")
    @ApiResponse(responseCode = "404", description = "O agendamento não existe ou não é de um pet deste tutor")
    public AgendamentoResponse buscar(@PathVariable Long id) {
        posse.exigirAgendamento(id);
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Marcar um compromisso",
            description = "O pet informado precisa ser do tutor autenticado.")
    @ApiResponse(responseCode = "201", description = "Criado")
    @ApiResponse(responseCode = "404", description = "O pet não existe ou não é deste tutor")
    public ResponseEntity<AgendamentoResponse> criar(@Valid @RequestBody AgendamentoRequest request) {
        // antes de qualquer coisa: nada do corpo e usado enquanto o pet nao for dele
        posse.exigirPet(request.petId());

        AgendamentoResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * As duas conferencias sao necessarias, e por motivos diferentes.
     *
     * <p>A do agendamento diz que ele pode mexer neste compromisso; a do pet
     * impede que ele use o proprio compromisso para ocupar a agenda em nome do
     * pet de outra pessoa.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Remarcar um compromisso",
            description = "O agendamento e o pet informado precisam ser do tutor autenticado.")
    @ApiResponse(responseCode = "404", description = "O agendamento ou o pet não é deste tutor")
    public AgendamentoResponse remarcar(@PathVariable Long id,
                                        @Valid @RequestBody AgendamentoRequest request) {
        posse.exigirAgendamento(id);
        posse.exigirPet(request.petId());
        return service.update(id, request);
    }

    /**
     * Cancelar, e nao apagar.
     *
     * <p>O compromisso vira CANCELADO pela mesma transicao de status que a API
     * de gestao usa: a linha continua existindo, some da agenda do dia e devolve
     * o horario para quem quiser. Apagar levaria junto o historico e esbarraria
     * na obrigacao que aponta para ele.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar um compromisso",
            description = "Cancelamento por transição de status; o registro permanece.")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    @ApiResponse(responseCode = "404", description = "O agendamento não existe ou não é de um pet deste tutor")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        posse.exigirAgendamento(id);
        service.updateStatus(id,
                new AgendamentoStatusRequest(AgendamentoStatus.CANCELADO, MOTIVO_DO_TUTOR));
        return ResponseEntity.noContent().build();
    }
}
