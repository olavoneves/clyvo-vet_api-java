package br.com.clyvovet.server.obrigacao.web;

import br.com.clyvovet.server.agendamento.AgendaService;
import br.com.clyvovet.server.agendamento.CompromissoDaAgenda;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

/**
 * Agenda operacional: quem a clinica atende hoje e ate o fim da semana.
 *
 * <p><b>A tela lista compromissos marcados, pela data em que acontecem</b> — nao
 * obrigacoes pela data em que venciam. A diferenca nao e cosmetica. Enquanto a
 * janela era a data prevista, uma obrigacao vencida e remarcada para a semana
 * seguinte nao aparecia em lugar nenhum: nem no dia previsto, que ja tinha
 * passado, nem no dia da consulta. Some da tela justamente o caso que o produto
 * existe para produzir — a receita recuperada.
 *
 * <p>Duas janelas da mesma consulta. A da semana comeca no dia seguinte para nao
 * repetir o que ja aparece no bloco de hoje: o mesmo compromisso listado duas
 * vezes faria a clinica ligar duas vezes para o tutor.
 */
@Controller
@RequiredArgsConstructor
public class AgendaWebController {

    /** Ate o setimo dia contado de hoje. */
    private static final int DIAS_DA_SEMANA = 7;

    private final AgendaService agenda;

    @GetMapping("/agenda")
    public String lista(Model model) {
        LocalDate hoje = LocalDate.now();

        List<CompromissoDaAgenda> doDia = agenda.compromissosEntre(hoje, hoje);
        List<CompromissoDaAgenda> daSemana =
                agenda.compromissosEntre(hoje.plusDays(1), hoje.plusDays(DIAS_DA_SEMANA));

        model.addAttribute("secao", "agenda");
        model.addAttribute("hoje", hoje);
        model.addAttribute("fimDaSemana", hoje.plusDays(DIAS_DA_SEMANA));
        model.addAttribute("doDia", doDia);
        model.addAttribute("daSemana", daSemana);
        // o numero que a clinica quer ver primeiro: quantos destes compromissos
        // sao obrigacao vencida que voltou para a agenda
        model.addAttribute("recuperadosNaSemana",
                doDia.stream().filter(CompromissoDaAgenda::recuperaAtraso).count()
                        + daSemana.stream().filter(CompromissoDaAgenda::recuperaAtraso).count());

        return "agenda/lista";
    }
}
