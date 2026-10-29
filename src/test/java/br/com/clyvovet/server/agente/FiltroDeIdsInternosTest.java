package br.com.clyvovet.server.agente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O filtro de ids, isolado.
 *
 * <p>Como no {@link GuardrailClinicoTest}, os dois lados importam: o que sai prova
 * que o filtro existe, e o que fica prova que ele nao estraga a confirmacao. Um
 * filtro que comesse a hora do agendamento seria pior que o id.
 */
class FiltroDeIdsInternosTest {

    private final FiltroDeIdsInternos filtro = new FiltroDeIdsInternos();

    @ParameterizedTest(name = "limpa: {0}")
    @CsvSource(delimiter = '|', value = {
            // o caso visto no site
            "Pendente: reavaliação renal trimestral (id 14273).|Pendente: reavaliação renal trimestral.",
            "A reavaliação id 14273 está pendente.|A reavaliação está pendente.",
            "A vacina (#14273) vence em 10/11.|A vacina vence em 10/11.",
            "A vacina #14273 vence em 10/11.|A vacina vence em 10/11.",
            "Reforço anual (idObrigacao: 101), previsto para 15/09.|Reforço anual, previsto para 15/09.",
            "Agendamento id_agendamento=777 confirmado.|Agendamento confirmado.",
            "Vermifugação (ID 55) e reforço anual (id 56) estão em aberto.|Vermifugação e reforço anual estão em aberto.",
            "Exame (código 3021) marcado para 05/11.|Exame marcado para 05/11."
    })
    void removeIdInterno(String resposta, String esperado) {
        assertThat(filtro.limpar(resposta)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "mantem: {0}")
    @ValueSource(strings = {
            "Marcado para 03/11 às 09:00. A Nala vai fazer a reavaliação renal.",
            "Tenho segunda, 03/11, às 09:00; terça às 14:30; ou quarta às 10:00.",
            "O reforço anual está previsto para 2026-11-15, dentro de 30 dias.",
            "A Nala tem 3 cuidados pendentes. Opção 1 é a mais próxima.",
            "Com a idade de 8 anos, o protocolo prevê o check-up anual.",
            "Ida e volta no mesmo dia: chegue 10 minutos antes.",
            "Você é o tutor #1 da fila? Não, brincadeira: o horário é 09:00."
    })
    void mantemNumeroQueNaoEId(String resposta) {
        assertThat(filtro.limpar(resposta)).isEqualTo(resposta);
    }

    @Test
    void respostaSemIdVoltaAMesmaInstancia() {
        String resposta = "Marcado para 03/11 às 09:00.";
        assertThat(filtro.limpar(resposta)).isSameAs(resposta);
    }

    @Test
    void nuloEVazioPassamDireto() {
        assertThat(filtro.limpar(null)).isNull();
        assertThat(filtro.limpar("")).isEmpty();
    }
}
