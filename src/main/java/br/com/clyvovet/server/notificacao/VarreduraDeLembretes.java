package br.com.clyvovet.server.notificacao;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.clinica.ClinicaRepository;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.obrigacao.ObrigacaoRepository;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.obrigacao.TransicaoRequest;
import br.com.clyvovet.server.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Persegue a obrigacao no lugar do colaborador.
 *
 * <p>O produto afirma que o motor cobra sozinho. Ate aqui a afirmacao era falsa:
 * a obrigacao nascia PREVISTA e ficava parada ate alguem abrir a ficha do pet e
 * apertar "Enviar lembrete". Quem perseguia era a pessoa, e o que o sistema
 * fazia era guardar a lista do que ela deveria ter feito.
 *
 * <p><b>A varredura nao emite nada.</b> Ela chama a mesma transicao que o botao
 * chama — {@code ObrigacaoService.transitar} para NOTIFICADA — e o lembrete
 * nasce la, como consequencia, junto com a trilha de transicao e o evento de
 * outbox. Um job que escrevesse notificacao direto criaria um segundo caminho
 * para a caixa do tutor, e o dia em que os dois divergissem ninguem saberia qual
 * dos dois estava certo.
 *
 * <h2>O grupo de controle</h2>
 *
 * <p>E a razao de esta classe ter um teste proprio. Os ~10% de pets sorteados
 * para o controle existem para medir o produto contra a inercia: se eles
 * receberem lembrete, a coorte de controle vira uma copia da tratada e o painel
 * passa a comparar o produto consigo mesmo. Uma varredura ingenua de "toda
 * obrigacao PREVISTA" destruiria o experimento na <i>primeira</i> execucao, de
 * forma irreversivel — nao ha como desnotificar. A exclusao vive no WHERE da
 * consulta, sobre a flag persistida no sorteio, e nao numa condicao aqui que
 * alguem possa reordenar sem perceber.
 *
 * <h2>Tenant</h2>
 *
 * <p>O job roda fora de requisicao, entao nao ha JWT, nao ha TenantFilter e o
 * {@code TenantContext} nasce vazio — com ele vazio o {@code @Filter} do
 * Hibernate resolve para {@code SEM_TENANT} e todo carregamento de entidade
 * devolve nada. Por isso a varredura itera as clinicas explicitamente,
 * preenchendo o contexto a cada volta e limpando no {@code finally}: ThreadLocal
 * sujo entre iteracoes faria a clinica seguinte transitar obrigacoes com o
 * tenant da anterior, que e vazamento entre clinicas com aparencia de bug de
 * relatorio.
 *
 * <h2>Idempotencia</h2>
 *
 * <p>Rodar duas vezes seguidas nao notifica duas vezes, e nao por acaso: a
 * consulta so devolve PREVISTA, e a primeira passada tira a obrigacao desse
 * estado. A segunda nao a encontra. Vale para <b>uma instancia</b> — duas
 * instancias varrendo ao mesmo tempo leem a mesma lista antes de qualquer uma
 * escrever, e ai a garantia passa a ser do banco (o EmissorDeLembretes recusa
 * lembrete repetido e a V12 tem indice unico), com as duas tentando transitar a
 * mesma linha. A versao multi-instancia precisa de lock distribuido; esta
 * limitacao esta registrada no README.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VarreduraDeLembretes {

    private static final String MOTIVO = "Lembrete automático: janela de antecedência aberta";

    private final ClinicaRepository clinicaRepository;
    private final ObrigacaoRepository obrigacaoRepository;
    private final ObrigacaoService obrigacaoService;
    private final LembreteProperties propriedades;

    /**
     * Uma passada por clinica, uma transicao por obrigacao.
     *
     * <p>Sem {@code @Transactional} aqui de proposito: cada {@code transitar}
     * abre a sua, entao uma obrigacao que falhe — dado inconsistente, transicao
     * que o motor recusa — nao arrasta as outras da mesma clinica junto no
     * rollback. Uma transacao para a varredura inteira transformaria um erro
     * pontual num dia sem lembrete nenhum.
     */
    @Scheduled(cron = "${app.lembretes.cron}", zone = "${app.lembretes.fuso}")
    public void varrer() {
        if (!propriedades.habilitado()) {
            return;
        }

        int total = 0;
        for (Clinica clinica : clinicaRepository.findAll()) {
            total += varrerClinica(clinica.getId());
        }

        if (total > 0) {
            log.info("Varredura de lembretes: {} obrigacoes levadas a NOTIFICADA", total);
        }
    }

    private int varrerClinica(Long idClinica) {
        try {
            TenantContext.set(idClinica);

            List<Long> pendentes = obrigacaoRepository.idsComLembreteEmAberto(
                    idClinica, Limit.of(propriedades.maxPorExecucao()));

            int enviados = 0;
            for (Long idObrigacao : pendentes) {
                enviados += notificar(idObrigacao) ? 1 : 0;
            }
            return enviados;
        } finally {
            // no finally, e nao no fim do try: uma excecao aqui deixaria o
            // ThreadLocal apontando para esta clinica na proxima iteracao
            TenantContext.clear();
        }
    }

    /**
     * Uma obrigacao que falha nao pode parar a varredura.
     *
     * <p>O job roda sem ninguem olhando. Se a excecao subisse, o agendador
     * marcaria a execucao como falha e as obrigacoes seguintes — de todas as
     * clinicas seguintes — ficariam sem lembrete ate o dia seguinte, por causa de
     * uma linha. O erro vai para o log com o id, que e o que permite investigar.
     */
    private boolean notificar(Long idObrigacao) {
        try {
            obrigacaoService.transitar(idObrigacao, new TransicaoRequest(
                    ObrigacaoStatus.NOTIFICADA, MOTIVO, null, null, null));
            return true;
        } catch (RuntimeException ex) {
            log.warn("Lembrete automatico falhou para a obrigacao {}: {}",
                    idObrigacao, ex.getMessage());
            return false;
        }
    }
}
