package br.com.clyvovet.server.obrigacao.web;

import br.com.clyvovet.server.agendamento.AgendamentoResponse;
import br.com.clyvovet.server.agendamento.AgendamentoService;
import br.com.clyvovet.server.agendamento.AgendamentoStatusRequest;
import br.com.clyvovet.server.enums.AgendamentoStatus;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Os botoes "Confirmar" e "Recusar" da agenda, para o pedido que o tutor fez
 * pelo aplicativo e que ainda espera a clinica.
 *
 * <p><b>Nao ha regra nova aqui.</b> Os dois fazem a mesma transicao que o
 * {@code PATCH /api/agendamentos/{id}/status} ja fazia —
 * {@code AgendamentoService.updateStatus} —, e o motivo da recusa passa pela
 * mesma validacao do {@link AgendamentoStatusRequest}. Se a tela tivesse o
 * proprio caminho, a primeira mudanca na API faria os dois divergirem.
 *
 * <p>Os status sao os que o aplicativo do tutor espera: ele compara a agenda
 * sincronizada com o ultimo status conhecido e avisa "A clinica confirmou" ao
 * ver CONFIRMADO, e "A clinica cancelou ... Motivo: ..." ao ver CANCELADO com
 * {@code motivoCancelamento}. Recusar e cancelar com motivo, por isso o motivo e
 * obrigatorio: e a frase que o tutor le.
 *
 * <p>Fica sob {@code /agenda/**}, que a cadeia das paginas ja reserva para
 * veterinario e colaborador — os mesmos perfis do PATCH na API.
 */
@Controller
@RequiredArgsConstructor
public class PedidoDoAppWebController {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final AgendamentoService service;
    private final Validator validator;

    @PostMapping("/agenda/{id}/confirmar")
    public String confirmar(@PathVariable Long id,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                            LocalDate inicio,
                            @RequestParam(required = false) Boolean minha,
                            RedirectAttributes redirect) {
        AgendamentoResponse confirmado = service.updateStatus(id,
                new AgendamentoStatusRequest(AgendamentoStatus.CONFIRMADO, null));

        redirect.addFlashAttribute("avisoOk", "Pedido de " + quem(confirmado)
                + " confirmado. O tutor é avisado no aplicativo.");
        return voltar(inicio, minha);
    }

    @PostMapping("/agenda/{id}/recusar")
    public String recusar(@PathVariable Long id,
                          @RequestParam(required = false) String motivo,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                          LocalDate inicio,
                          @RequestParam(required = false) Boolean minha,
                          RedirectAttributes redirect) {
        String texto = motivo == null ? "" : motivo.strip();
        var recusa = new AgendamentoStatusRequest(AgendamentoStatus.CANCELADO, texto);

        // o campo e required na tela, mas um POST direto chega sem ele; e o
        // tamanho e o mesmo limite que a API aplica ao corpo do PATCH
        if (texto.isEmpty() || !validator.validate(recusa).isEmpty()) {
            redirect.addFlashAttribute("avisoErro",
                    "Para recusar, escreva um motivo curto: é ele que o tutor lê no aplicativo.");
            return voltar(inicio, minha);
        }

        AgendamentoResponse recusado = service.updateStatus(id, recusa);

        redirect.addFlashAttribute("avisoOk", "Pedido de " + quem(recusado)
                + " recusado. O tutor recebe o motivo no aplicativo.");
        return voltar(inicio, minha);
    }

    private static String quem(AgendamentoResponse a) {
        return a.petNome() + " em " + a.dtAgendamento().format(DIA) + " às " + a.hrAgendamento();
    }

    /** Volta para a mesma semana e o mesmo recorte de onde o botao foi apertado. */
    private static String voltar(LocalDate inicio, Boolean minha) {
        return "redirect:" + UriComponentsBuilder.fromPath("/agenda")
                .queryParamIfPresent("inicio", Optional.ofNullable(inicio))
                .queryParamIfPresent("minha", Optional.ofNullable(minha))
                .toUriString();
    }
}
