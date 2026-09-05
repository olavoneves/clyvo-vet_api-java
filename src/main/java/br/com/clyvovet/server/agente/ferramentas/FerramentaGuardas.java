package br.com.clyvovet.server.agente.ferramentas;

import br.com.clyvovet.server.agente.ContextoDoAgente;
import br.com.clyvovet.server.exception.EntityNotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Conferencias que toda ferramenta faz sobre o que o modelo mandou.
 *
 * <p>O modelo escreve os argumentos, e o texto do tutor influencia o que ele
 * escreve. Nenhum argumento vira acesso a dado sem passar por aqui.
 */
final class FerramentaGuardas {

    private FerramentaGuardas() {
    }

    /**
     * O pet do argumento tem que ser o pet da conversa.
     *
     * <p>404 e nao 403: quem perguntou nao pode descobrir, pela diferenca entre
     * as respostas, que o pet existe em outra clinica.
     */
    static void exigirPetDaConversa(Long idPet, ContextoDoAgente contexto) {
        if (!contexto.idPet().equals(idPet)) {
            throw new EntityNotFoundException("Pet", idPet);
        }
    }

    /**
     * Aceita {@code 2026-09-15T14:00} e {@code 2026-09-15 14:00}.
     *
     * <p>O modelo alterna entre as duas formas sem aviso. Recusar a segunda
     * gastaria uma volta do laco para o modelo descobrir sozinho qual e a certa.
     */
    static LocalDateTime dataHora(String texto) {
        String normalizado = texto.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalizado);
        } catch (DateTimeParseException ignorado) {
            try {
                // "2026-09-15T14" ou data sem hora: completa com o inicio do dia
                return LocalDate.parse(texto.trim()).atTime(LocalTime.MIDNIGHT);
            } catch (DateTimeParseException tambemIgnorado) {
                throw new IllegalArgumentException(
                        "Data e hora devem estar no formato AAAA-MM-DDTHH:mm, como 2026-09-15T14:00");
            }
        }
    }

    static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException ignorado) {
            throw new IllegalArgumentException(
                    "Data deve estar no formato AAAA-MM-DD, como 2026-09-15");
        }
    }
}
