package br.com.clyvovet.server.agente.ferramentas;

import br.com.clyvovet.server.agendamento.AgendaService;
import br.com.clyvovet.server.agente.AcaoDoAgente;
import br.com.clyvovet.server.agente.ContextoDoAgente;
import br.com.clyvovet.server.agente.Esquemas;
import br.com.clyvovet.server.agente.Ferramenta;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Marca a consulta e devolve a obrigacao para o trilho.
 *
 * <p>E o unico ponto do agente que escreve estado de negocio, e o que fecha a
 * cadeia de valor: o agendamento nasce com {@code ds_canal_origem = 'APP'} e a
 * obrigacao correspondente passa a AGENDADA pela procedure, entrando no painel
 * de receita recuperada.
 */
@Component
@RequiredArgsConstructor
public class CriarAgendamento implements Ferramenta {

    private final AgendaService agendaService;
    private final ObrigacaoService obrigacaoService;

    @Override
    public String nome() {
        return "criar_agendamento";
    }

    @Override
    public String descricao() {
        return """
                                Marca a consulta para uma obrigação pendente do pet, em um horário que \
                consultar_disponibilidade tenha devolvido como livre. \
                Use apenas depois que o tutor escolher explicitamente um horário — nunca \
                escolha por ele. Se o horário tiver sido ocupado nesse meio-tempo, a \
                ferramenta devolve um erro de conflito: consulte a disponibilidade de novo \
                e ofereça outras opções. \
                Ao final, confirme ao tutor a data e a hora e diga o que será feito na \
                consulta. Não diga qual veterinário vai atender.""";
    }

    @Override
    public Map<String, Object> esquema() {
        return Esquemas.objeto(
                Esquemas.inteiro("idObrigacao",
                        "Id da obrigação vinda de listar_obrigacoes_pendentes."),
                Esquemas.texto("dataHora",
                        "Início da consulta, no formato AAAA-MM-DDTHH:mm, como 2026-09-15T14:00."),
                Esquemas.inteiro("idVeterinario",
                        "Id do veterinário da vaga escolhida, vindo de consultar_disponibilidade."));
    }

    @Override
    public Resultado executar(Map<String, Object> entrada, ContextoDoAgente contexto) {
        Long idObrigacao = Esquemas.inteiroDe(entrada, "idObrigacao");
        LocalDateTime quando = FerramentaGuardas.dataHora(Esquemas.textoDe(entrada, "dataHora"));
        Long idVeterinario = Esquemas.inteiroDe(entrada, "idVeterinario");

        exigirObrigacaoDoPet(idObrigacao, contexto);

        AgendaService.AgendamentoConfirmado confirmado =
                agendaService.agendar(idObrigacao, quando, idVeterinario);

        AcaoDoAgente acao = new AcaoDoAgente(AcaoDoAgente.AGENDAMENTO_CRIADO,
                confirmado.idAgendamento(),
                "Consulta marcada para " + confirmado.data() + " às " + confirmado.hora()
                        + " com " + confirmado.veterinario());

        return Resultado.escrita(confirmado, acao);
    }

    /**
     * A obrigacao tem que ser deste pet.
     *
     * <p>O filtro de tenant ja garante que ela e desta clinica, mas nao que e do
     * pet da conversa: sem esta checagem, um id chutado marcaria consulta para o
     * cachorro de outro cliente da mesma clinica.
     */
    private void exigirObrigacaoDoPet(Long idObrigacao, ContextoDoAgente contexto) {
        boolean doPet = obrigacaoService.findById(idObrigacao)
                .obrigacao().petId().equals(contexto.idPet());
        if (!doPet) {
            throw new EntityNotFoundException("Obrigacao", idObrigacao);
        }
    }
}
