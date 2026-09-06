package br.com.clyvovet.server.notificacao;

import br.com.clyvovet.server.enums.CanalPreferencial;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * O canal do proprio aplicativo: a caixa de entrada do tutor.
 *
 * <p>Nao ha efeito externo nenhum, e por isso este metodo parece vazio. Ele nao
 * e. A entrega neste canal <b>e</b> a linha ter sido gravada: quando o tutor
 * abre a tela, o lembrete esta la. Um canal externo — push, WhatsApp — teria
 * aqui a chamada ao provedor, e e exatamente essa diferenca que a porta existe
 * para absorver.
 *
 * <p>O log fica porque e a unica pista, numa demonstracao, de que o lembrete
 * saiu no momento em que a obrigacao mudou de estado.
 */
@Slf4j
@Component
public class CanalApp implements CanalDeNotificacao {

    @Override
    public CanalPreferencial canal() {
        return CanalPreferencial.APP;
    }

    @Override
    public void entregar(Notificacao notificacao) {
        log.info("Lembrete na caixa do tutor {}: obrigacao={} titulo={}",
                notificacao.getTutor().getId(),
                notificacao.getObrigacao() == null ? null : notificacao.getObrigacao().getId(),
                notificacao.getTitulo());
    }
}
