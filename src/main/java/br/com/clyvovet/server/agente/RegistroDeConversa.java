package br.com.clyvovet.server.agente;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.enums.OutboxStatus;
import br.com.clyvovet.server.outbox.OutboxEvent;
import br.com.clyvovet.server.outbox.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grava cada turno da conversa no outbox de dominio.
 *
 * <p>Um evento por turno, e nao a conversa inteira reescrita a cada mensagem: e
 * assim que o outbox funciona no resto do sistema, e e o que permite ao
 * clyvo-insights consumir o fluxo depois sem reprocessar historico. A conversa
 * completa se reconstroi ordenando os eventos do mesmo pet por data.
 *
 * <p>{@code REQUIRES_NEW} de proposito. O registro nao pode ser desfeito pela
 * falha do que veio depois: se o agente conseguiu responder e algo quebrou na
 * sequencia, o que ele disse ao tutor precisa continuar existindo — inclusive, e
 * principalmente, quando a resposta foi barrada pelo guardrail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistroDeConversa {

    public static final String TIPO_DE_EVENTO = "ConversaAgente";
    private static final String AGREGADO = "CONVERSA_AGENTE";
    private static final int VERSAO_DO_EVENTO = 1;

    /** ds_ultimo_erro e VARCHAR2(600); o payload e CLOB, mas nao ha razao para engordar. */
    private static final int MAX_TEXTO = 4000;

    private final OutboxEventRepository repository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /**
     * @param guardrail categoria barrada na saida, ou nulo quando a resposta passou
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(ContextoDoAgente contexto, String pergunta, String resposta,
                          List<String> ferramentas, List<AcaoDoAgente> acoes,
                          boolean escalado, String guardrail) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("idPet", contexto.idPet());
            payload.put("idTutor", contexto.idTutor());
            payload.put("pergunta", cortar(pergunta));
            payload.put("resposta", cortar(resposta));
            payload.put("ferramentas", ferramentas);
            payload.put("acoes", acoes);
            payload.put("escalado", escalado);
            payload.put("guardrail", guardrail);

            OutboxEvent evento = new OutboxEvent();
            evento.setDsEventUuid(UUID.randomUUID().toString());
            evento.setDsEventType(TIPO_DE_EVENTO);
            evento.setNrEventVersion(VERSAO_DO_EVENTO);
            evento.setDsCorrelationId(UUID.randomUUID().toString());
            evento.setClinica(entityManager.getReference(Clinica.class, contexto.idClinica()));
            evento.setNmUsuario(contexto.emailDoTutor());
            evento.setDsAgregado(AGREGADO);
            evento.setNrIdAgregado(contexto.idPet());
            evento.setDsPayload(objectMapper.writeValueAsString(payload));
            evento.setDtOcorrencia(LocalDateTime.now());
            evento.setDsStatus(OutboxStatus.PENDENTE);
            evento.setNrTentativas(0);

            repository.save(evento);

        } catch (RuntimeException ex) {
            // auditoria nao derruba atendimento: o tutor ja tem a resposta na tela
            log.error("Falha ao registrar turno do agente para o pet {}", contexto.idPet(), ex);
        }
    }

    private static String cortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= MAX_TEXTO ? texto : texto.substring(0, MAX_TEXTO) + "…";
    }
}
