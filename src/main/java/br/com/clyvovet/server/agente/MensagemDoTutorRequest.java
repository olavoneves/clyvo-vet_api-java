package br.com.clyvovet.server.agente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Uma mensagem do tutor.
 *
 * <p>O teto de caracteres nao e formalidade: tudo que chega aqui vira prompt, e
 * prompt sem limite e uma conta de token sem limite. Duas mil letras cobrem
 * qualquer coisa que alguem digite sobre marcar uma consulta.
 */
public record MensagemDoTutorRequest(
        @NotNull Long idPet,
        @NotBlank @Size(max = 2000) String texto
) {
}
