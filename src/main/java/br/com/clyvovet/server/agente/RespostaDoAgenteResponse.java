package br.com.clyvovet.server.agente;

import java.util.List;

/**
 * O que o tutor recebe de volta.
 *
 * @param texto   a resposta a ser exibida; nunca vazia, nem quando algo falhou
 * @param acoes   o que mudou no sistema neste turno
 * @param escalado se este turno terminou nas maos de um veterinario
 */
public record RespostaDoAgenteResponse(
        String texto,
        List<AcaoDoAgente> acoes,
        boolean escalado
) {
}
