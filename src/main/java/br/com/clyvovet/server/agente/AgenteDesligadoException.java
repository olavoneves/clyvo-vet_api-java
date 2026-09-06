package br.com.clyvovet.server.agente;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * O provedor de LLM ativo nao tem chave: o agente nao existe neste ambiente.
 *
 * <p>503 e nao 500 porque nao ha erro nenhum — a aplicacao sobe inteira sem a
 * chave (GEMINI_API_KEY ou ANTHROPIC_API_KEY, conforme
 * {@code app.agente.provedor}), de proposito, e todo o resto do sistema
 * continua funcionando. Quem
 * chamou precisa saber que este recurso especifico esta fora, nao que algo
 * quebrou.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AgenteDesligadoException extends RuntimeException {

    public AgenteDesligadoException() {
        super("Agente de agendamento indisponível nesta instalação");
    }
}
