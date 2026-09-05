package br.com.clyvovet.server.agente.ferramentas;

import br.com.clyvovet.server.agendamento.AgendaService;
import br.com.clyvovet.server.agente.ContextoDoAgente;
import br.com.clyvovet.server.agente.Esquemas;
import br.com.clyvovet.server.agente.Ferramenta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Horarios livres na agenda real da clinica.
 *
 * <p>O spec original desta ferramenta previa {@code idClinica} como parametro.
 * Ele nao existe aqui: a clinica vem do {@link ContextoDoAgente}, que vem do
 * TenantContext. Um id de clinica no esquema seria um id que o texto do tutor
 * pode influenciar — e disponibilidade de agenda de outra clinica e exatamente o
 * tipo de vazamento que o filtro de tenant existe para impedir.
 */
@Component
@RequiredArgsConstructor
public class ConsultarDisponibilidade implements Ferramenta {

    /** Faixa maxima por consulta: alem disso a resposta vira ruido para o modelo. */
    private static final int MAX_DIAS = 60;

    private final AgendaService agendaService;

    @Override
    public String nome() {
        return "consultar_disponibilidade";
    }

    @Override
    public String descricao() {
        return """
                Consulta os horários livres na agenda da clínica em um intervalo de datas. \
                Devolve data, hora e o veterinário de cada vaga, já descontando o que está \
                marcado. A clínica atende de segunda a sexta, das 09:00 às 12:00 e das \
                13:00 às 18:00, em consultas de 30 minutos. \
                Ofereça ao tutor no máximo três ou quatro opções por vez, em linguagem \
                natural, e guarde o veterinário de cada opção para usar ao agendar.""";
    }

    @Override
    public Map<String, Object> esquema() {
        return Esquemas.objeto(
                Esquemas.texto("dataInicio", "Primeiro dia da busca, no formato AAAA-MM-DD."),
                Esquemas.texto("dataFim", "Último dia da busca, no formato AAAA-MM-DD. "
                        + "No máximo " + MAX_DIAS + " dias depois de dataInicio."));
    }

    @Override
    public Resultado executar(Map<String, Object> entrada, ContextoDoAgente contexto) {
        LocalDate inicio = FerramentaGuardas.data(Esquemas.textoDe(entrada, "dataInicio"));
        LocalDate fim = FerramentaGuardas.data(Esquemas.textoDe(entrada, "dataFim"));

        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("dataFim não pode ser anterior a dataInicio");
        }
        LocalDate limite = inicio.plusDays(MAX_DIAS);
        LocalDate efetivo = fim.isAfter(limite) ? limite : fim;

        List<AgendaService.HorarioLivre> livres = agendaService.horariosLivres(inicio, efetivo);

        return Resultado.leitura(Map.of(
                "de", inicio.toString(),
                "ate", efetivo.toString(),
                "quantidade", livres.size(),
                "horarios", livres));
    }
}
