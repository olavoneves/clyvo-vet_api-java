package br.com.clyvovet.server.agente;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A porta do agente de agendamento.
 *
 * <p>Exclusiva do tutor — quem entra por aqui e quem tem ROLE_TUTOR, e a
 * {@code SecurityConfig} nao deixa outro perfil chegar. Que o tutor so alcance
 * os proprios pets nao e decidido aqui: e o {@code AgenteService} que confronta
 * o pet pedido com o dono autenticado, porque a mesma regra precisa valer para
 * as duas rotas e para toda ferramenta que o modelo acionar.
 *
 * <p>Sem chave de API configurada, as duas rotas devolvem 503 — ver
 * {@link AgenteDesligadoException}.
 */
@RestController
@RequestMapping("/agente")
@RequiredArgsConstructor
@Tag(name = "Agente", description = "Agente de agendamento conversacional do tutor")
public class AgenteController {

    private final AgenteService service;

    @PostMapping("/mensagens")
    @Operation(summary = "Enviar uma mensagem ao agente de agendamento")
    @ApiResponse(responseCode = "200", description = "Resposta do agente")
    @ApiResponse(responseCode = "404", description = "O pet não é deste tutor")
    @ApiResponse(responseCode = "503", description = "Agente indisponível nesta instalação")
    public RespostaDoAgenteResponse enviar(@Valid @RequestBody MensagemDoTutorRequest request) {
        return service.responder(request.idPet(), request.texto());
    }

    @GetMapping("/conversas/{idPet}")
    @Operation(summary = "Histórico da conversa sobre um pet")
    @ApiResponse(responseCode = "404", description = "O pet não é deste tutor")
    public ConversaResponse conversa(@PathVariable Long idPet) {
        return service.historico(idPet);
    }
}
