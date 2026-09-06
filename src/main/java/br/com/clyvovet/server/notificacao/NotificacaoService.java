package br.com.clyvovet.server.notificacao;

import br.com.clyvovet.server.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A caixa de entrada do tutor.
 *
 * <p>Todo metodo recebe o id do tutor e o usa <b>dentro</b> da consulta, nunca
 * numa checagem depois de ler. E a mesma regra que o agente segue: quem pede so
 * alcanca o que e seu, e um id de outro tutor nao volta da consulta em vez de
 * voltar e ser recusado.
 */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository repository;

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> caixaDo(Long idTutor) {
        return repository.daCaixaDo(idTutor).stream()
                .map(NotificacaoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public long naoLidasDo(Long idTutor) {
        return repository.countByTutorIdAndDtLeituraIsNull(idTutor);
    }

    /**
     * Marca como lida e devolve para onde o tutor vai.
     *
     * <p>Devolver o destino, em vez de so marcar, mantem a decisao de rota do
     * lado que sabe o que a notificacao e: quem veio de obrigacao leva para a
     * conversa com o agente sobre aquele pet; o que nao tem obrigacao atras — e
     * ainda nao existe, mas vai existir — cairia na propria caixa em vez de numa
     * rota inventada.
     */
    @Transactional
    public String abrir(Long idNotificacao, Long idTutor) {
        Notificacao notificacao = repository.findByIdAndTutorId(idNotificacao, idTutor)
                .orElseThrow(() -> new EntityNotFoundException("Notificação", idNotificacao));

        notificacao.marcarLida();

        if (notificacao.getObrigacao() == null) {
            return "/tutor";
        }
        return "/tutor/pets/" + notificacao.getObrigacao().getPet().getId();
    }
}
