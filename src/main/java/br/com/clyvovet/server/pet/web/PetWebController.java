package br.com.clyvovet.server.pet.web;

import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.pet.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Lista e ficha do pet.
 *
 * <p>Consome os mesmos services da API. O filtro de tenant do Hibernate cuida
 * do recorte: a lista nao recebe id_clinica nenhum, e um pet de outra clinica
 * simplesmente nao existe para esta sessao.
 */
@Controller
@RequiredArgsConstructor
public class PetWebController {

    /** Obrigacoes na ficha do pet: cabe a linha do tempo recente, nao o historico inteiro. */
    private static final Pageable OBRIGACOES_DA_FICHA =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "dtPrevista"));

    private final PetService petService;
    private final ObrigacaoService obrigacaoService;

    @GetMapping("/pets")
    public String lista(@PageableDefault(size = 20, sort = "nome") Pageable pageable, Model model) {
        model.addAttribute("secao", "pets");
        model.addAttribute("pagina", petService.findAll(pageable));
        return "pets/lista";
    }

    @GetMapping("/pets/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("secao", "pets");
        model.addAttribute("pet", petService.getFichaTecnica(id));
        model.addAttribute("obrigacoes",
                obrigacaoService.buscar(null, null, null, id, OBRIGACOES_DA_FICHA).getContent());
        return "pets/detalhe";
    }
}
