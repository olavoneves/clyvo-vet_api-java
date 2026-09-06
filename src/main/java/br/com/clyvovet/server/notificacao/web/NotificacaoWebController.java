package br.com.clyvovet.server.notificacao.web;

import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.exception.UnauthorizedException;
import br.com.clyvovet.server.notificacao.NotificacaoService;
import br.com.clyvovet.server.pet.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * A caixa de entrada do tutor, e o clique que sai dela.
 *
 * <p>Abrir e um GET porque e um link numa lista, e nao um formulario — mas ele
 * muda estado, marcando a notificacao como lida. E uma troca deliberada: exigir
 * POST aqui obrigaria cada linha da lista a virar um formulario, e o efeito
 * colateral e idempotente e sem risco (marcar lida duas vezes nao muda nada, e
 * {@code marcarLida} preserva a primeira data).
 */
@Controller
@RequiredArgsConstructor
public class NotificacaoWebController {

    private final NotificacaoService service;
    private final PetService petService;

    @GetMapping("/tutor/caixa")
    public String caixa(Model model) {
        AuthenticatedUser tutor = autenticado();

        model.addAttribute("secao", "caixa");
        model.addAttribute("tutor", tutor);
        // o cabecalho e o mesmo fragmento da tela do pet, e ele lista os pets
        model.addAttribute("pets", petService
                .findByTutor(tutor.id(), PageRequest.of(0, 50, Sort.by("nome"))).getContent());
        model.addAttribute("notificacoes", service.caixaDo(tutor.id()));
        model.addAttribute("naoLidas", service.naoLidasDo(tutor.id()));

        return "tutor/caixa";
    }

    /**
     * Abre a notificacao e leva ao fluxo que ja existe.
     *
     * <p>O destino vem do servico, e nao e montado aqui: quem sabe se a
     * notificacao tem obrigacao atras — e portanto se ha conversa de agente para
     * abrir — e quem carregou a entidade.
     */
    @GetMapping("/tutor/caixa/{id}")
    public String abrir(@PathVariable Long id) {
        return "redirect:" + service.abrir(id, autenticado().id());
    }

    private AuthenticatedUser autenticado() {
        return AuthenticatedUser.atual()
                .orElseThrow(() -> new UnauthorizedException("Sessão sem usuário autenticado"));
    }
}
