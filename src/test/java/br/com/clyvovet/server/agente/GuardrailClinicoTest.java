package br.com.clyvovet.server.agente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A segunda camada do guardrail, isolada.
 *
 * <p>Dois conjuntos de casos, e os dois importam igualmente. Os proibidos provam
 * que a barreira existe; os permitidos provam que ela nao inutiliza o agente. Um
 * guardrail que barrasse "posso marcar a vacina?" seria tao inutil quanto um que
 * deixasse passar uma dosagem — so que o segundo problema aparece na banca e o
 * primeiro aparece com o primeiro tutor.
 */
class GuardrailClinicoTest {

    private final GuardrailClinico guardrail = new GuardrailClinico();

    @ParameterizedTest(name = "barra: {0}")
    @ValueSource(strings = {
            "Pode dar 250 mg de dipirona a cada 8 horas.",
            "A dose é de 10 mg/kg, uma vez ao dia.",
            "Dê 2 comprimidos hoje e mais 2 amanhã.",
            "Administre o medicamento antes da refeição.",
            "A posologia recomendada para o porte dele é essa.",
            "Pelo que você descreveu, o diagnóstico mais provável é gastrite.",
            "Isso é suspeita de verminose, mas dá para tratar em casa.",
            "Provavelmente é uma reação à vacina, nada demais.",
            "Esse sintoma indica uma infecção de ouvido.",
            "Vômito depois de comer é normal em filhotes.",
            "Não é grave, pode esperar a próxima consulta.",
            "Trate com antibiótico por sete dias.",
            "Aplique o anti-inflamatório que sobrou da última vez.",
            "Não precisa levar ele à clínica por causa disso."
    })
    void barraConteudoClinico(String resposta) {
        assertThat(guardrail.conteudoClinico(resposta))
                .as("deveria ter sido barrada: %s", resposta)
                .isPresent();
    }

    @ParameterizedTest(name = "libera: {0}")
    @ValueSource(strings = {
            "Tenho quinta às 14:00 e sexta às 09:30 com a Dra. Ana. Qual prefere?",
            "A vacina V10 do Rex está prevista para 15/09. Quer marcar?",
            "Consulta marcada para 15/09 às 14:00 com o Dr. Paulo.",
            "Remarquei para segunda às 10:00. A vermifugação continua pendente.",
            "Não encontrei horário nessa semana. Posso ver a semana seguinte?",
            "O reforço anual e a vermifugação estão em aberto para o Bidu.",
            "Esse horário acabou de ser ocupado. Tenho 15:30 no mesmo dia.",
            "A aplicação da vacina leva cerca de 30 minutos."
    })
    void liberaConversaDeAgenda(String resposta) {
        assertThat(guardrail.conteudoClinico(resposta))
                .as("não deveria ter sido barrada: %s", resposta)
                .isEmpty();
    }

    /**
     * A propria mensagem de escalonamento nao pode disparar o guardrail.
     *
     * <p>Se disparasse, o texto de substituicao seria substituido por ele mesmo a
     * cada turno — funcionaria por acidente, e quebraria no dia em que alguem
     * reescrevesse a frase.
     */
    @Test
    @DisplayName("a mensagem de escalonamento passa pelo proprio guardrail")
    void mensagemDeEscalonamentoNaoDisparaAProprioGuardrail() {
        assertThat(guardrail.conteudoClinico(MensagensDoAgente.ESCALONAMENTO)).isEmpty();
        assertThat(guardrail.conteudoClinico(MensagensDoAgente.INDISPONIVEL)).isEmpty();
        assertThat(guardrail.conteudoClinico(MensagensDoAgente.SEM_CONCLUSAO)).isEmpty();
    }

    @Test
    void respostaVaziaNaoQuebra() {
        assertThat(guardrail.conteudoClinico(null)).isEmpty();
        assertThat(guardrail.conteudoClinico("   ")).isEmpty();
    }
}
