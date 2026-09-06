package br.com.clyvovet.server.obrigacao.web;

import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.exception.ConflitoDeEstadoException;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.obrigacao.TransicaoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * O botao que a clinica aperta para antecipar um lembrete.
 *
 * <p><b>E a excecao, e nao o caminho.</b> O disparo padrao e a
 * {@code VarreduraDeLembretes}, que roda todo dia e leva a NOTIFICADA toda
 * obrigacao cuja janela de antecedencia abriu. Este botao serve para o caso em
 * que a clinica quer avisar antes da hora — o tutor que ligou, a vaga que
 * apareceu. Dai o nome na tela ser "Antecipar lembrete": um botao chamado
 * "Enviar lembrete" dizia que sem ele nada saia, que era verdade e deixou de ser.
 *
 * <p><b>Nao emite nada por conta propria.</b> Ele faz a mesma transicao que a API
 * ja fazia — {@code ObrigacaoService.transitar} —, e e la que o lembrete nasce.
 * Se este controller emitisse direto, existiriam dois caminhos para a mesma
 * coisa e o da tela poderia divergir do da API na primeira mudanca.
 */
@Controller
@RequiredArgsConstructor
public class LembreteWebController {

    private static final String MOTIVO = "Lembrete antecipado pela clínica";

    private final ObrigacaoService service;

    @PostMapping("/obrigacoes/{id}/lembrete")
    public String enviar(@PathVariable Long id) {
        // A tela ja nao mostra o botao para pet de controle, mas esconder o botao
        // nao fecha a rota: um POST direto — link antigo, tela em cache, curl de
        // alguem testando — notificaria o controle e destruiria o experimento sem
        // deixar rastro de intencao. A guarda que vale e esta.
        var alvo = service.findById(id).obrigacao();
        if (Boolean.TRUE.equals(alvo.grupoControle())) {
            throw new ConflitoDeEstadoException(
                    "Pet do grupo de controle não recebe lembrete: é dele que sai a"
                            + " medida do que acontece sem o produto.");
        }

        var detalhe = service.transitar(id, new TransicaoRequest(
                ObrigacaoStatus.NOTIFICADA, MOTIVO, null, null, null));

        // volta para a ficha de onde o botao foi apertado, e nao para uma tela de
        // confirmacao: o resultado ja e visivel na propria linha da tabela
        return "redirect:/pets/" + detalhe.obrigacao().petId();
    }
}
