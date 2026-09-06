package br.com.clyvovet.server.obrigacao.web;

import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.obrigacao.TransicaoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * O botao que a clinica aperta para o lembrete sair.
 *
 * <p>Sem ele a cadeia de valor tinha um buraco na demonstracao: o motor gerava a
 * obrigacao, a caixa do tutor sabia mostrar lembretes, e nao havia como ir de uma
 * coisa a outra sem chamar a API na mao. O elo existia no codigo e nao na tela.
 *
 * <p><b>Nao emite nada por conta propria.</b> Ele faz a mesma transicao que a API
 * ja fazia — {@code ObrigacaoService.transitar} —, e e la que o lembrete nasce.
 * Se este controller emitisse direto, existiriam dois caminhos para a mesma
 * coisa e o da tela poderia divergir do da API na primeira mudanca.
 */
@Controller
@RequiredArgsConstructor
public class LembreteWebController {

    private static final String MOTIVO = "Lembrete enviado pela clínica";

    private final ObrigacaoService service;

    @PostMapping("/obrigacoes/{id}/lembrete")
    public String enviar(@PathVariable Long id) {
        var detalhe = service.transitar(id, new TransicaoRequest(
                ObrigacaoStatus.NOTIFICADA, MOTIVO, null, null, null));

        // volta para a ficha de onde o botao foi apertado, e nao para uma tela de
        // confirmacao: o resultado ja e visivel na propria linha da tabela
        return "redirect:/pets/" + detalhe.obrigacao().petId();
    }
}
