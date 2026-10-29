package br.com.clyvovet.server.obrigacao.web;

import br.com.clyvovet.server.agendamento.AgendaService;
import br.com.clyvovet.server.agendamento.CompromissoDaAgenda;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.enums.TipoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agenda operacional: quem a clinica atende numa semana, a partir de um dia.
 *
 * <p><b>A tela lista compromissos marcados, pela data em que acontecem</b> — nao
 * obrigacoes pela data em que venciam. A diferenca nao e cosmetica. Enquanto a
 * janela era a data prevista, uma obrigacao vencida e remarcada para a semana
 * seguinte nao aparecia em lugar nenhum: nem no dia previsto, que ja tinha
 * passado, nem no dia da consulta. Some da tela justamente o caso que o produto
 * existe para produzir — a receita recuperada.
 *
 * <p>A semana comeca em {@code ?inicio=AAAA-MM-DD}, hoje por padrao, e anda de
 * sete em sete dias. Uma consulta so para os sete dias, separada por dia aqui:
 * o mesmo compromisso nunca aparece em dois blocos.
 *
 * <p>"Minha agenda" recorta pelo veterinario da sessao, e so existe para ele —
 * colaborador nao tem agenda propria, e o parametro e ignorado. O recorte por
 * clinica continua vindo do filtro de tenant do Hibernate, nao daqui.
 */
@Controller
@RequiredArgsConstructor
public class AgendaWebController {

    /** A janela da tela: o dia de inicio e os seis seguintes. */
    private static final int DIAS_DA_SEMANA = 7;

    /**
     * O nome do dia sai em portugues seja qual for o idioma do navegador: a
     * clinica le "quinta-feira", e a tela nao tem outra traducao.
     */
    private static final DateTimeFormatter ROTULO_DO_DIA =
            DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.of("pt", "BR"));

    private final AgendaService agenda;

    /** Um dia da semana exibida, com os compromissos dele em ordem de horario. */
    public record DiaDaAgenda(LocalDate data, boolean hoje, List<CompromissoDaAgenda> compromissos) {

        public String rotulo() {
            return hoje ? "Hoje · " + data.format(DateTimeFormatter.ofPattern("dd/MM"))
                    : data.format(ROTULO_DO_DIA);
        }
    }

    @GetMapping("/agenda")
    public String lista(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                        LocalDate inicio,
                        @RequestParam(required = false) Boolean minha,
                        Model model) {
        LocalDate hoje = LocalDate.now();
        LocalDate de = inicio == null ? hoje : inicio;
        LocalDate ate = de.plusDays(DIAS_DA_SEMANA - 1);

        Long veterinarioLogado = AuthenticatedUser.atual()
                .filter(usuario -> usuario.tipo() == TipoUsuario.VETERINARIO)
                .map(AuthenticatedUser::id)
                .orElse(null);
        // ligado por padrao para o veterinario: e a pergunta que ele traz para a tela
        boolean minhaAgenda = veterinarioLogado != null && (minha == null || minha);

        List<CompromissoDaAgenda> daSemana =
                agenda.compromissosEntre(de, ate, minhaAgenda ? veterinarioLogado : null);

        model.addAttribute("secao", "agenda");
        model.addAttribute("hoje", hoje);
        model.addAttribute("inicio", de);
        model.addAttribute("fim", ate);
        model.addAttribute("semanaAnterior", de.minusDays(DIAS_DA_SEMANA));
        model.addAttribute("proximaSemana", de.plusDays(DIAS_DA_SEMANA));
        model.addAttribute("ehVeterinario", veterinarioLogado != null);
        model.addAttribute("minhaAgenda", minhaAgenda);
        model.addAttribute("dias", porDia(de, hoje, daSemana));
        model.addAttribute("vazia", daSemana.isEmpty());
        // o numero que a clinica quer ver primeiro: quantos destes compromissos
        // sao obrigacao vencida que voltou para a agenda
        model.addAttribute("recuperadosNaSemana",
                daSemana.stream().filter(CompromissoDaAgenda::recuperaAtraso).count());

        return "agenda/lista";
    }

    /**
     * Os sete dias da janela, cada um com o que tem marcado.
     *
     * <p>Dia util entra mesmo vazio — "nada marcado na quinta" e informacao para
     * quem encaixa consulta. Sabado e domingo so entram com compromisso: a
     * clinica nao atende, e dois blocos vazios toda semana sao ruido. Hoje entra
     * sempre, porque e o dia que a recepcao abre a tela para ver.
     */
    private static List<DiaDaAgenda> porDia(LocalDate de, LocalDate hoje,
                                            List<CompromissoDaAgenda> compromissos) {
        Map<LocalDate, List<CompromissoDaAgenda>> agrupados = compromissos.stream()
                .collect(Collectors.groupingBy(CompromissoDaAgenda::data));

        List<DiaDaAgenda> dias = new ArrayList<>(DIAS_DA_SEMANA);
        for (int i = 0; i < DIAS_DA_SEMANA; i++) {
            LocalDate dia = de.plusDays(i);
            List<CompromissoDaAgenda> doDia = agrupados.getOrDefault(dia, List.of());
            boolean fimDeSemana = dia.getDayOfWeek() == DayOfWeek.SATURDAY
                    || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (fimDeSemana && doDia.isEmpty() && !dia.equals(hoje)) {
                continue;
            }
            dias.add(new DiaDaAgenda(dia, dia.equals(hoje), doDia));
        }
        return dias;
    }
}
