package br.com.clyvovet.server.atendimento.web;

import br.com.clyvovet.server.atendimento.AtendimentoForm;
import br.com.clyvovet.server.atendimento.AtendimentoRegistrado;
import br.com.clyvovet.server.atendimento.RegistroDeAtendimentoService;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.enums.EventoGatilho;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.exception.ConflitoDeEstadoException;
import br.com.clyvovet.server.pet.PetService;
import br.com.clyvovet.server.veterinario.VeterinarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * A tela "Registrar atendimento", aberta a partir da ficha do pet.
 *
 * <p>Fecha o buraco do fluxo principal: ate aqui uma consulta so nascia pela
 * API, em duas chamadas, e a demonstracao precisava de Postman no meio. A tela
 * nao decide nada — quem gera obrigacao e o motor, chamado pelo mesmo servico
 * que a API usa.
 *
 * <p>Fica sob {@code /pets/**}, que a cadeia das paginas ja reserva para
 * veterinario e colaborador: os mesmos perfis que alcancam
 * {@code /api/consultas/**}.
 */
@Controller
@RequiredArgsConstructor
public class AtendimentoWebController {

    /** Uma clinica tem poucos veterinarios: cabem todos no seletor. */
    private static final Pageable VETERINARIOS =
            PageRequest.of(0, 200, Sort.by(Sort.Direction.ASC, "nome"));

    /** Rotulos dos gatilhos. O EnumMap mantem a ordem do enum no seletor. */
    private static final Map<EventoGatilho, String> GATILHOS = new EnumMap<>(Map.of(
            EventoGatilho.CONSULTA_REGISTRADA, "Consulta registrada",
            EventoGatilho.CONDICAO_DIAGNOSTICADA, "Condição diagnosticada",
            EventoGatilho.VACINA_APLICADA, "Vacina aplicada",
            EventoGatilho.PROCEDIMENTO_REALIZADO, "Procedimento realizado",
            EventoGatilho.PESO_ATUALIZADO, "Peso atualizado",
            EventoGatilho.FASE_VIDA_ALTERADA, "Mudança de fase de vida",
            EventoGatilho.PET_CADASTRADO, "Pet cadastrado"));

    private final PetService petService;
    private final VeterinarioService veterinarioService;
    private final RegistroDeAtendimentoService registroService;

    @GetMapping("/pets/{id}/atendimentos/novo")
    public String novo(@PathVariable Long id, Model model) {
        AtendimentoForm form = new AtendimentoForm();
        form.setDtConsulta(LocalDate.now());
        form.setEventoGatilho(EventoGatilho.CONSULTA_REGISTRADA);
        // o veterinario logado e quem atendeu, quase sempre; colaborador escolhe
        AuthenticatedUser.atual()
                .filter(usuario -> usuario.tipo() == TipoUsuario.VETERINARIO)
                .map(AuthenticatedUser::id)
                .ifPresent(form::setVeterinarioId);
        return formulario(id, form, model);
    }

    @PostMapping("/pets/{id}/atendimentos")
    public String registrar(@PathVariable Long id,
                            @Valid @ModelAttribute("atendimento") AtendimentoForm form,
                            BindingResult erros,
                            Model model,
                            RedirectAttributes redirect) {
        if (erros.hasErrors()) {
            return formulario(id, form, model);
        }

        AtendimentoRegistrado registrado;
        try {
            registrado = registroService.registrar(id, form);
        } catch (ConflitoDeEstadoException recusa) {
            // o motor recusou: a transacao ja voltou, consulta inclusive, e o
            // que ele disse vai para a tela com o que foi digitado
            erros.reject("motor", recusa.getMessage());
            return formulario(id, form, model);
        }

        redirect.addFlashAttribute("atendimentoRegistrado", registrado);
        return "redirect:/pets/" + id;
    }

    private String formulario(Long petId, AtendimentoForm form, Model model) {
        model.addAttribute("secao", "pets");
        model.addAttribute("pet", petService.getFichaTecnica(petId));
        model.addAttribute("atendimento", form);
        model.addAttribute("veterinarios", veterinarioService.findAll(VETERINARIOS).getContent());
        model.addAttribute("gatilhos", GATILHOS);
        return "atendimentos/novo";
    }
}
