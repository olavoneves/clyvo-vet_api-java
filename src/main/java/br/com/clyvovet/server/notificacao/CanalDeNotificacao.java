package br.com.clyvovet.server.notificacao;

import br.com.clyvovet.server.enums.CanalPreferencial;

/**
 * Por onde um lembrete sai.
 *
 * <p>Uma interface e uma implementacao. Push, WhatsApp e e-mail entram depois
 * como classes novas, e o motor de protocolo continua sem saber que existem —
 * ele produz a transicao, quem escuta e {@code EmissorDeLembretes}, e quem
 * entrega e esta porta.
 *
 * <p><b>Nao e um framework de notificacao.</b> Nao ha roteamento por
 * preferencia, retentativa, agendamento de envio nem template engine, e nao
 * deve haver ate existir um segundo canal de verdade pedindo por isso. O que
 * esta interface garante hoje e uma coisa so: que acrescentar um canal seja
 * escrever uma classe, e nao mexer no lugar onde a obrigacao muda de estado.
 */
public interface CanalDeNotificacao {

    /** Qual canal esta implementacao atende. */
    CanalPreferencial canal();

    /**
     * Entrega o lembrete.
     *
     * <p>A notificacao ja nasceu persistida quando chega aqui: a persistencia e
     * o registro do que o tutor tem para ver, e a entrega e o efeito externo. Um
     * canal que falhe ao entregar nao pode apagar o que ja foi registrado — daí
     * a assinatura receber a entidade em vez de devolver uma.
     */
    void entregar(Notificacao notificacao);
}
