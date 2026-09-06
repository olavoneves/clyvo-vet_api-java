package br.com.clyvovet.server.notificacao;

import br.com.clyvovet.server.clinica.ClinicaRepository;
import br.com.clyvovet.server.obrigacao.ObrigacaoRepository;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A varredura que faz a afirmacao do produto virar verdade — e as quatro coisas
 * que quebram se ela for ingenua.
 *
 * <p>Roda dentro da transacao do teste. {@code ObrigacaoService.transitar} e
 * {@code REQUIRED}, entao ele <b>entra</b> nesta transacao em vez de abrir uma
 * propria, e o rollback do fim leva junto tudo o que a procedure escreveu. Sem
 * isso o teste notificaria a base de verdade a cada execucao.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle com o motor de protocolo instalado")
class VarreduraDeLembretesIntegracaoTest {

    /**
     * Datas absurdamente antigas, e de proposito.
     *
     * <p>A consulta ordena por {@code dt_prevista}, entao uma obrigacao de 1989
     * vem antes de qualquer coisa que exista na base. Isso permite manter o teto
     * por execucao em 3 e ainda ter certeza de que as linhas fabricadas foram as
     * escolhidas — o teste fica rapido e nao depende de quantas obrigacoes o seed
     * gerou.
     */
    private static final String VENCIMENTO_DO_CONTROLE = "1989-01-01";
    private static final String VENCIMENTO_DO_TRATADO = "1990-01-01";

    private static final String MARCA_CONTROLE = "teste-varredura-controle";
    private static final String MARCA_TRATADO = "teste-varredura-tratado";

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private ObrigacaoRepository obrigacaoRepository;

    @Autowired
    private ObrigacaoService obrigacaoService;

    @Autowired
    private EntityManager entityManager;

    private VarreduraDeLembretes varredura;
    private Long clinica;

    @BeforeEach
    void preparar() {
        List<Long> clinicas = entityManager
                .createNativeQuery("select id_clinica from TB_CLV_CLINICA order by id_clinica", Long.class)
                .getResultList();
        Assumptions.assumeFalse(clinicas.isEmpty(), "nenhuma clinica cadastrada");
        clinica = clinicas.getFirst();
        TenantContext.set(clinica);

        // construida a mao, e nao injetada, para poder apertar o teto por
        // execucao: com 3 o teste faz tres transicoes em vez das milhares que o
        // seed deixaria pendentes
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
     * <p>A obrigacao de controle e a <b>mais antiga das duas</b>: pela ordenacao
     * da consulta ela seria a primeira da fila. Se a exclusao do grupo de
     * controle sumir do WHERE, este teste falha antes de qualquer outro, que e
     * exatamente o que se quer — notificar o controle nao tem desfazer, e o
     * experimento morre em silencio na primeira execucao do job.
     */
    @Test
    @DisplayName("o grupo de controle nao e notificado, mesmo sendo o primeiro da fila")
    void controleNuncaEntraNaVarredura() {
        Long controle = fabricar(MARCA_CONTROLE, VENCIMENTO_DO_CONTROLE, "S");
        Long tratado = fabricar(MARCA_TRATADO, VENCIMENTO_DO_TRATADO, "N");

        assertThat(obrigacaoRepository.idsComLembreteEmAberto(clinica, Limit.of(3)))
                .as("o tratado vencido tem que estar na fila")
                .contains(tratado)
                .as("o controle nao pode estar na fila em hipotese nenhuma")
                .doesNotContain(controle);

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
        Long tratado = fabricar(MARCA_TRATADO, VENCIMENTO_DO_TRATADO, "N");

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
        Long tratado = fabricar(MARCA_TRATADO, VENCIMENTO_DO_TRATADO, "N");

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
        Long futura = fabricar("teste-varredura-futura", null, "N");

        assertThat(obrigacaoRepository.idsComLembreteEmAberto(clinica, Limit.of(500)))
                .as("40 dias a frente esta fora de qualquer antecedencia padrao")
                .doesNotContain(futura);

        entityManager.createNativeQuery("""
                update TB_CLV_PROTOCOLO set nr_antecedencia_lembrete_dias = 60
                 where id_protocolo = (
                       select v.id_protocolo from TB_CLV_OBRIGACAO o
                         join TB_CLV_ETAPA_PROTOCOLO e  on e.id_etapa = o.id_etapa
                         join TB_CLV_VERSAO_PROTOCOLO v on v.id_versao_protocolo = e.id_versao_protocolo
                        where o.id_obrigacao = %d)
                """.formatted(futura)).executeUpdate();
        entityManager.flush();

        assertThat(obrigacaoRepository.idsComLembreteEmAberto(clinica, Limit.of(500)))
                .as("com 60 dias de antecedencia no catalogo, a mesma obrigacao entra")
                .contains(futura);
    }

    // ---------- fixture ----------

    /**
     * Uma obrigacao fabricada para este teste, copiada de uma linha real.
     *
     * <p>Pet, versao e etapa vem de uma obrigacao que ja existe, porque precisam
     * ser coerentes entre si e com o catalogo clinico; o que o teste controla e
     * so o que ele esta testando — vencimento e grupo. {@code dataIso} nula
     * significa 40 dias no futuro, fora de qualquer janela padrao.
     */
    private Long fabricar(String marca, String dataIso, String grupoControle) {
        Long modelo = idNativo("""
                select min(id_obrigacao) from TB_CLV_OBRIGACAO where id_clinica = %d
                """.formatted(clinica));
        Assumptions.assumeTrue(modelo != null, "a clinica precisa de uma obrigacao de molde");

        String prevista = dataIso == null
                ? "TRUNC(SYSDATE) + 40"
                : "DATE '%s'".formatted(dataIso);

        entityManager.createNativeQuery("""
                insert into TB_CLV_OBRIGACAO
                       (id_clinica, id_pet, id_versao_protocolo, id_etapa,
                        dt_prevista, dt_janela_inicio, dt_janela_fim,
                        ds_status, ds_correlation_id, fl_grupo_controle)
                select id_clinica, id_pet, id_versao_protocolo, id_etapa,
                       %s, %s - 7, %s + 30,
                       'PREVISTA', '%s', '%s'
                  from TB_CLV_OBRIGACAO where id_obrigacao = %d
                """.formatted(prevista, prevista, prevista, marca, grupoControle, modelo))
                .executeUpdate();
        entityManager.flush();

        return idNativo("""
                select max(id_obrigacao) from TB_CLV_OBRIGACAO where ds_correlation_id = '%s'
                """.formatted(marca));
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

    private Long idNativo(String sql) {
        Object resultado = entityManager.createNativeQuery(sql).getSingleResult();
        return resultado == null ? null : ((Number) resultado).longValue();
    }
}
