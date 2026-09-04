package br.com.clyvovet.server.agente.ferramentas;

import br.com.clyvovet.server.agendamento.AgendaService;
import br.com.clyvovet.server.agendamento.AgendamentoService;
import br.com.clyvovet.server.agente.AcaoDoAgente;
import br.com.clyvovet.server.agente.ContextoDoAgente;
import br.com.clyvovet.server.agente.Esquemas;
import br.com.clyvovet.server.agente.Ferramenta;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/** Move para outro horario uma consulta que ja estava marcada. */
@Component
@RequiredArgsConstructor
public class Reagendar implements Ferramenta {

    private final AgendaService agendaService;
    private final AgendamentoService agendamentoService;

    @Override
    public String nome() {
        return "reagendar";
    }

    @Override
    public String descricao() {
        return """
                Move uma consulta já marcada para outro horário livre, mantendo o mesmo \
                veterinário. Use quando o tutor pedir para adiar ou antecipar algo que já \
                tem horário. Confirme o novo horário com ele antes de chamar, e consulte \
                consultar_disponibilidade para saber o que está livre.""";
    }

    @Override
    public Map<String, Object> esquema() {
        return Esquemas.objeto(
                Esquemas.inteiro("idAgendamento", "Id do agendamento existente."),
                Esquemas.texto("novaDataHora",
                        "Novo início da consulta, no formato AAAA-MM-DDTHH:mm."));
    }

    @Override
    public Resultado executar(Map<String, Object> entrada, ContextoDoAgente contexto) {
        Long idAgendamento = Esquemas.inteiroDe(entrada, "idAgendamento");
        LocalDateTime nova = FerramentaGuardas.dataHora(Esquemas.textoDe(entrada, "novaDataHora"));

        // mesmo motivo da checagem em criar_agendamento: o tenant nao separa dois
        // pets da mesma clinica, e a conversa e sobre um so
        if (!agendamentoService.findById(idAgendamento).petId().equals(contexto.idPet())) {
            throw new EntityNotFoundException("Agendamento", idAgendamento);
        }

        AgendaService.AgendamentoConfirmado confirmado =
                agendaService.reagendar(idAgendamento, nova);

        AcaoDoAgente acao = new AcaoDoAgente(AcaoDoAgente.AGENDAMENTO_REMARCADO,
                confirmado.idAgendamento(),
                "Consulta remarcada para " + confirmado.data() + " às " + confirmado.hora()
                        + " com " + confirmado.veterinario());

        return Resultado.escrita(confirmado, acao);
    }
}
