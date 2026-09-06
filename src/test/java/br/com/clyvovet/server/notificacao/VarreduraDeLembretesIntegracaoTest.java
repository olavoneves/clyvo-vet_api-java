package br.com.clyvovet.server.notificacao;

import br.com.clyvovet.server.clinica.ClinicaRepository;
import br.com.clyvovet.server.obrigacao.ObrigacaoRepository;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.support.CenarioClinico;
import br.com.clyvovet.server.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A varredura que faz a afirmacao do produto virar verdade — e as quatro coisas
 * que quebram se ela for ingenua.
 *
 * <p>Roda dentro da transacao do teste. {@code ObrigacaoService.transitar} e
 * {@code REQUIRED}, entao ele <b>entra</b> nesta transacao em vez de abrir uma
 * propria, e o rollback do fim leva junto tudo o que a procedure escreveu. Sem
 * isso o teste notificaria a base de verdade a cada execucao.
 *
 * <p>A clinica e o pet sao fabricados: numa clinica so deste teste as duas
 * obrigacoes montadas abaixo sao as unicas candidatas, entao a fila que a
 * consulta devolve e exatamente a que o teste escreveu — sem depender de quantas
 * obrigacoes o seed deixou pendentes nem de quais delas a varredura de ontem ja
 * consumiu.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle com o motor de protocolo instalado")
class VarreduraDeLembretesIntegracaoTest {

    /**
     * Vencimentos no passado, e o do controle mais antigo que o do tratado.
     *
     * <p>A consulta ordena por {@code dt_prevista}, entao a obrigacao de controle
     * e a <b>primeira da fila</b> — e seria a primeira notificada se a exclusao
     * do grupo de controle sumisse do WHERE.
     */
    private static final int VENCIMENTO_DO_CONTROLE = -60;
    private static final int VENCIMENTO_DO_TRATADO = -30;

    /** Fora de qualquer antecedencia do catalogo, cujo maximo hoje e 15 dias. */
    private static final int VENCIMENTO_DISTANTE = 40;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private ObrigacaoRepository obrigacaoRepository;

    @Autowired
    private ObrigacaoService obrigacaoService;

    @Autowired
    private EntityManager entityManager;

    private CenarioClinico fixture;
    private CenarioClinico.Cenario cenario;
    private VarreduraDeLembretes varredura;
    private Long clinica;

    @BeforeEach
    void preparar() {
        fixture = new CenarioClinico(entityManager);
        cenario = fixture.clinicaCompleta("Clinica da varredura");
        clinica = cenario.clinica();
        TenantContext.set(clinica);

        // construida a mao, e nao injetada, para poder apertar o teto por
        // execucao: com 3 a varredura faz poucas transicoes nas clinicas do seed,
        // que ela tambem percorre, e o teste nao fica preso nelas
        varredura = new VarreduraDeLembretes(clinicaRepository, obrigacaoRepository,
                obrigacaoService, new LembreteProperties(true, "0 0 8 * * *", "America/Sao_Paulo", 3));
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    /**
     * O teste que justifica os outros.
     *
     * <p>Se a exclusao do grupo de controle sumir do WHERE, este teste falha antes
     * de qualquer outro, que e exatamente o que se quer — notificar o controle nao
     * tem desfazer, e o experimento morre em silencio na primeira execucao do job.
     */
    @Test
    @DisplayName("o grupo de controle nao e notificado, mesmo sendo o primeiro da fila")
    void controleNuncaEntraNaVarredura() {
        Long controle = obrigacao(VENCIMENTO_DO_CONTROLE, true);
        Long tratado = obrigacao(VENCIMENTO_DO_TRATADO, false);

        assertThat(obrigacaoRepository.idsComLembreteEmAberto(clinica, Limit.of(10)))
                .as("nesta clinica so existem estas duas obrigacoes")
                .containsExactly(tratado);

        varredura.varrer();
        entityManager.flush();
        entityManager.clear();

        assertThat(statusDe(controle))
                .as("pet de controle notificado destroi a medida contra a inercia")
                .isEqualTo("PREVISTA");
        assertThat(statusDe(tratado)).isEqualTo("NOTIFICADA");

        assertThat(notificacoesDe(controle))
                .as("e nao pode sobrar notificacao nenhuma na caixa do tutor dele")
                .isZero();
    }

    /**
     * A transicao e o job; a notificacao e consequencia dela.
     *
     * <p>Se um dia alguem fizer a varredura gravar notificacao direto, este teste
     * continua passando — e por isso ele tambem olha a trilha de transicao. Sem
     * ela, o lembrete existiria sem que o motor tivesse decidido nada.
     */
    @Test
    @DisplayName("a varredura leva a NOTIFICADA e a caixa do tutor recebe o lembrete")
    void varreduraNotificaPelaTransicao() {
        Long tratado = obrigacao(VENCIMENTO_DO_TRATADO, false);

        varredura.varrer();
        entityManager.flush();
        entityManager.clear();

        assertThat(statusDe(tratado)).isEqualTo("NOTIFICADA");
        assertThat(notificacoesDe(tratado))
                .as("a caixa de entrada do tutor tem que funcionar sem alteracao nenhuma")
                .isEqualTo(1);
        assertThat(contar("""
                select count(*) from TB_CLV_OBRIGACAO_TRANSICAO
                 where id_obrigacao = %d and ds_status_para = 'NOTIFICADA'
                """.formatted(tratado)))
                .as("sem trilha de transicao o lembrete nasceu por fora do motor")
                .isEqualTo(1);
    }

    /**
     * Idempotencia, com instancia unica: e o proprio filtro de estado que a da.
     *
     * <p>A obrigacao sai de PREVISTA na primeira passada, e a consulta so devolve
     * PREVISTA. A segunda passada nao a encontra — nao ha marcacao de "ja
     * notificado" a manter em lugar nenhum. Este teste existe para confirmar que
     * e assim <i>de fato</i>, e nao so no raciocinio.
     */
    @Test
    @DisplayName("varrer duas vezes seguidas nao notifica duas vezes")
    void segundaVarreduraNaoRepete() {
        Long tratado = obrigacao(VENCIMENTO_DO_TRATADO, false);

        varredura.varrer();
        entityManager.flush();
        entityManager.clear();

        varredura.varrer();
        entityManager.flush();
        entityManager.clear();

        assertThat(notificacoesDe(tratado))
                .as("a segunda passada nao pode empilhar um segundo lembrete")
                .isEqualTo(1);
        assertThat(contar("""
                select count(*) from TB_CLV_OBRIGACAO_TRANSICAO
                 where id_obrigacao = %d and ds_status_para = 'NOTIFICADA'
                """.formatted(tratado)))
                .isEqualTo(1);
    }

    /**
     * A janela e do catalogo, e a prova e move-la sem recompilar nada.
     *
     * <p>Uma obrigacao que vence daqui a 40 dias esta fora de qualquer
     * antecedencia padrao. Subir a antecedencia do protocolo dela para 60 a traz
     * para dentro. Se a antecedencia fosse uma constante em Java, ou um
     * {@code if} por categoria, este teste nao teria como passar.
     */
    @Test
    @DisplayName("mudar a antecedencia no catalogo muda quem entra na fila")
    void aJanelaVemDoCatalogoENaoDoCodigo() {
        Long futura = obrigacao(VENCIMENTO_DISTANTE, false);

        assertThat(obrigacaoRepository.idsComLembreteEmAberto(clinica, Limit.of(10)))
                .as("40 dias a frente esta fora de qualquer antecedencia padrao")
                .isEmpty();

        entityManager.createNativeQuery("""
                update TB_CLV_PROTOCOLO set nr_antecedencia_lembrete_dias = 60
                 where id_protocolo = (
                       select v.id_protocolo from TB_CLV_OBRIGACAO o
                         join TB_CLV_ETAPA_PROTOCOLO e  on e.id_etapa = o.id_etapa
                         join TB_CLV_VERSAO_PROTOCOLO v on v.id_versao_protocolo = e.id_versao_protocolo
                        where o.id_obrigacao = %d)
                """.formatted(futura)).executeUpdate();
        entityManager.flush();

        assertThat(obrigacaoRepository.idsComLembreteEmAberto(clinica, Limit.of(10)))
                .as("com 60 dias de antecedencia no catalogo, a mesma obrigacao entra")
                .containsExactly(futura);
    }

    // ---------- fixture ----------

    private Long obrigacao(int diasAteVencer, boolean grupoControle) {
        return fixture.novaObrigacao(clinica, cenario.pet(), "PREVISTA",
                diasAteVencer, grupoControle);
    }

    private String statusDe(Long idObrigacao) {
        return (String) entityManager.createNativeQuery(
                        "select ds_status from TB_CLV_OBRIGACAO where id_obrigacao = " + idObrigacao)
                .getSingleResult();
    }

    private long notificacoesDe(Long idObrigacao) {
        return contar("select count(*) from TB_CLV_NOTIFICACAO where id_obrigacao = " + idObrigacao);
    }

    private long contar(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
