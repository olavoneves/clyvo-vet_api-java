package br.com.clyvovet.server.painel.web;

import br.com.clyvovet.server.painel.PainelReceitaResponse;
import br.com.clyvovet.server.painel.PainelReceitaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Painel de receita recuperada — a tela que sustenta o preco do produto.
 *
 * <p>Le o mesmo {@code PainelReceitaService} que a API: a pagina e outra forma
 * de apresentar o numero, nao outra forma de calcula-lo. O recorte por clinica
 * sai do TenantContext dentro do service, entao a pagina nao tem como pedir o
 * mes de outra clinica nem por engano.
 */
@Controller
@RequiredArgsConstructor
public class PainelWebController {

    private final PainelReceitaService service;

    /** A clinica entra pelo painel: e o que ela quer ver ao abrir o sistema. */
    @GetMapping("/")
    public String raiz() {
        return "redirect:/painel/receita";
    }

    @GetMapping("/painel/receita")
    public String receita(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mes,
            Model model) {

        LocalDate referencia = (mes != null ? mes : LocalDate.now()).withDayOfMonth(1);
        PainelReceitaResponse painel = service.doMes(referencia);

        model.addAttribute("secao", "painel");
        model.addAttribute("painel", painel);
        // as duas listas alimentam ao mesmo tempo o HTML e os graficos: um so
        // recorte dos dados, para a barra nunca contar algo diferente do texto
        model.addAttribute("etapasFunil", FunilView.de(painel.funil()));
        model.addAttribute("gruposComparacao", ComparacaoView.de(painel));
        model.addAttribute("mesAtual", referencia);
        model.addAttribute("mesAnterior", referencia.minusMonths(1));
        // o mes seguinte so faz sentido enquanto nao passa do mes corrente:
        // painel de mes futuro so mostraria zeros
        model.addAttribute("mesSeguinte",
                referencia.isBefore(LocalDate.now().withDayOfMonth(1)) ? referencia.plusMonths(1) : null);

        return "painel/receita";
    }
}
