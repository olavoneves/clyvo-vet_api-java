package br.com.clyvovet.server.tutor.app;

import br.com.clyvovet.server.tutor.TutorResponse;
import br.com.clyvovet.server.tutor.TutorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O perfil de quem esta com o aplicativo aberto.
 *
 * <p>Nao ha {@code /api/tutor/{id}}, e nunca havera: a superficie do tutor nao
 * tem como endereçar outro tutor. Quem lista e busca tutor por id e a API de
 * gestao, em {@code /api/tutores}, que so a clinica alcanca.
 */
@RestController
@RequestMapping("/tutor")
@RequiredArgsConstructor
@Tag(name = "Tutor: perfil", description = "Dados do tutor autenticado no aplicativo")
public class TutorPerfilController {

    private final TutorService tutorService;
    private final PosseDoTutor posse;

    @GetMapping("/me")
    @Operation(summary = "Dados do tutor autenticado",
            description = "O tutor sai do token; não há parâmetro de identificação.")
    @ApiResponse(responseCode = "200", description = "Perfil do tutor autenticado")
    @ApiResponse(responseCode = "403", description = "Perfil sem permissão para esta rota")
    public TutorResponse eu() {
        return tutorService.findById(posse.id());
    }
}
