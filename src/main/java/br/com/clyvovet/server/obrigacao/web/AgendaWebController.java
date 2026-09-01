package br.com.clyvovet.server.obrigacao.web;

import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

/**
 * Agenda operacional: o que a clinica tem para fazer hoje e ate o fim da semana.
 *
 * <p>Duas janelas do mesmo service de obrigacoes. A da semana comeca no dia
 * seguinte para nao repetir o que ja aparece no bloco de hoje — a mesma
 * obrigacao listada duas vezes faria a clinica ligar duas vezes para o tutor.
 */
@Controller
@RequiredArgsConstructor
public class AgendaWebController {

    private static final Pageable POR_DATA =
            PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "dtPrevista"));

    /** Ate o setimo dia contado de hoje. */
    private static final int DIAS_DA_SEMANA = 7;

    private final ObrigacaoService service;

    @GetMapping("/agenda")
    public String lista(Model model) {
        LocalDate hoje = LocalDate.now();

        model.addAttribute("secao", "agenda");
        model.addAttribute("hoje", hoje);
        model.addAttribute("fimDaSemana", hoje.plusDays(DIAS_DA_SEMANA));
        model.addAttribute("doDia",
                service.buscar(null, hoje, hoje, null, POR_DATA).getContent());
        model.addAttribute("daSemana",
                service.buscar(null, hoje.plusDays(1), hoje.plusDays(DIAS_DA_SEMANA), null, POR_DATA)
                        .getContent());

        return "agenda/lista";
    }
}
