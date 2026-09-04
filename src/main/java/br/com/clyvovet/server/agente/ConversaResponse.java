package br.com.clyvovet.server.agente;

import java.util.List;

/**
 * A conversa como uma pessoa a leria.
 *
 * <p>Deriva do historico guardado para o modelo, filtrando os blocos que so
 * existem para ele — chamada de ferramenta e resultado. O tutor quer rever o que
 * foi dito, nao como foi feito.
 */
public record ConversaResponse(
        Long idPet,
        boolean escalada,
        List<Turno> turnos
) {

    /** @param autor TUTOR ou AGENTE */
    public record Turno(String autor, String texto) {

        public static final String TUTOR = "TUTOR";
        public static final String AGENTE = "AGENTE";
    }
}
