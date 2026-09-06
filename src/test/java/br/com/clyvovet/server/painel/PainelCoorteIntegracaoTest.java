package br.com.clyvovet.server.painel;

import br.com.clyvovet.server.support.CenarioClinico;
import br.com.clyvovet.server.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.assertj.core.data.Offset;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VW_CLV_PAINEL_COORTE contra o banco.
 *
 * <p><b>O que este teste nao faz e o ponto principal dele.</b> Ele nao afirma
 * contagens. O seed usa {@code SYS_GUID} e datas relativas a {@code SYSDATE},
 * entao duas execucoes da mesma procedure produzem volumes diferentes — a
 * especificacao deste card trazia 2.449 obrigacoes tratadas e a execucao local
 * produziu 2.764, sem que nada estivesse errado. Fixar contagem aqui seria
 * escrever um teste que quebra no proximo seed sem dizer nada sobre a view.
 *
 * <p>O que e estavel, e portanto o que se afirma: as <b>taxas</b>, que saem do
 * sorteio de {@code PR_CLV_SEED_DESFECHOS}; a <b>aritmetica</b> da view,
 * conferida contra a tabela que ela resume; e a <b>forma</b> — um grupo de cada
 * por clinica, e o tratado acima do controle.
 *
 * <p><b>Tres dos quatro casos deixaram de depender do que a base tiver.</b> A
 * aritmetica da view e a conta do card montam a propria coorte, numa clinica so
 * deste teste — antes eles liam a primeira clinica que existisse, e o caso do
 * valor em reais desistia por {@code assumeTrue} sempre que a amostra daquela
 * clinica nao passava do piso, que e justamente a condicao que ele existe para
 * exercitar. E a proporcao do sorteio passou a ser medida chamando
 * {@code FN_CLV_GRUPO_CONTROLE} direto, sobre milhares de ids sinteticos, em vez
 * de contar pets semeados.
 *
 * <p>O unico caso que continua exigindo a base semeada e o da calibragem do
 * seed — nao ha como verificar que {@code PR_CLV_SEED_DESFECHOS} produz as taxas
 * que declara sem os dados que ele produziu. Esse caso <b>falha</b>, com
 * mensagem dizendo o que rodar, em vez de se pular em silencio.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle configurado nas variaveis de ambiente")
class PainelCoorteIntegracaoTest {

    /** Tolerancia das taxas: o sorteio e aleatorio, a media e que e estavel. */
    private static final double FOLGA_PP = 6.0;

    /**
     * A taxa que o seed sorteia, lida do proprio seed.
     *
     * <p>Antes eram duas constantes aqui, 58,0 e 36,0, copiadas de
     * {@code PR_CLV_SEED_DESFECHOS}. Copia de numero e copia de verdade: quem
     * recalibrasse o sorteio no banco veria este teste falhar sem nada estar
     * quebrado, e a leitura obvia da falha seria mexer no teste ate ele calar.
     *
     * <p>O default do parametro nao chega em {@code ALL_ARGUMENTS} — a coluna
     * {@code DEFAULT_VALUE} vem vazia para este caso —, entao a leitura e do
     * texto da procedure, que e onde o valor de fato esta. Feio de olhar e
     * honesto: existe uma fonte so, e e a mesma que o seed executa.
     *
     * <p>Devolve o literal como texto, e a conversao acontece em Java. Com
     * {@code TO_NUMBER} no SQL a consulta quebrava com ORA-01722 na sessao da
     * aplicacao enquanto passava no SQL*Plus: o driver herda o locale da JVM, e
     * em pt-BR o separador decimal da sessao vira virgula — entao o ponto do
     * literal deixa de ser separador. {@link BigDecimal} nao tem locale.
     */
    private static final String SQL_TAXA_DO_SEED = """
            select regexp_substr(text, 'DEFAULT\\s+([0-9]+\\.?[0-9]*)', 1, 1, NULL, 1)
              from user_source
             where name = 'PR_CLV_SEED_DESFECHOS'
               and upper(text) like '%' || :parametro || '%'
               and upper(text) like '%DEFAULT%'
            """;

    private static final String SQL_LINHAS = """
            select ds_grupo, qt_obrigacoes, qt_cumpridas, pc_cumprimento,
                   qt_pets, vl_ticket_medio
              from VW_CLV_PAINEL_COORTE
             where id_clinica = :id
            """;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PainelCoorteService service;

    @Autowired
    private EntityManager entityManager;

    private CenarioClinico fixture;

    @BeforeEach
    void preparar() {
        fixture = new CenarioClinico(entityManager);
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    /**
     * A calibragem do seed, sobre a base semeada.
     *
     * <p>Este e o unico caso da classe que precisa dos dados de demonstracao: nao
     * ha como conferir que {@code PR_CLV_SEED_DESFECHOS} produz as taxas que
     * declara sem olhar o que ele produziu. Antes ele se pulava quando a base
     * estava vazia; agora falha dizendo o que rodar, porque um banco de
     * demonstracao sem demonstracao e um defeito de ambiente, e nao um motivo
     * para o teste calar.
     */
    @Test
    @DisplayName("a view devolve um grupo tratado e um controle por clinica, com a taxa do sorteio")
    void viewDevolveOsDoisGruposComATaxaDoSorteio() {
        List<PainelCoorteService.Linha> linhas = linhasDa(primeiraClinicaSemeada());

        assertThat(linhas)
                .as("uma linha por grupo: tratado e controle")
                .hasSize(2)
                .extracting(PainelCoorteService.Linha::dsGrupo)
                .containsExactlyInAnyOrder("TRATADO", "CONTROLE");

        for (PainelCoorteService.Linha linha : linhas) {
            double esperada = taxaDoSeed("TRATADO".equals(linha.dsGrupo())
                    ? "P_TAXA_TRATADO" : "P_TAXA_CONTROLE");

            assertThat(linha.pcCumprimento().doubleValue())
                    .as("taxa do grupo %s: o seed sorteia perto de %s%%",
                            linha.dsGrupo(), esperada)
                    .isCloseTo(esperada, Offset.offset(FOLGA_PP));

            assertThat(linha.qtCumpridas())
                    .as("cumpridas nunca passam do total")
                    .isLessThanOrEqualTo(linha.qtObrigacoes());

            assertThat(linha.qtPets())
                    .as("obrigacao sem pet nao existe")
                    .isPositive();
        }
    }

    /**
     * A aritmetica da view, conferida contra a tabela que ela resume.
     *
     * <p>Sobre uma coorte fabricada: numeros redondos e escolhidos fazem a conta
     * ser conferivel de cabeca, e a clinica so deste teste garante que nao ha mais
     * nada na tabela para confundir o total.
     */
    @Test
    @DisplayName("a taxa da view bate com a contagem crua da tabela")
    void taxaDaViewBateComATabela() {
        Long clinica = coorteFabricada(20, 10, 20, 4).clinica();

        for (PainelCoorteService.Linha linha : linhasDa(clinica)) {
            String flag = "CONTROLE".equals(linha.dsGrupo()) ? "S" : "N";

            Long cruas = jdbcClient.sql("""
                    select count(*) from TB_CLV_OBRIGACAO
                     where id_clinica = :id and fl_grupo_controle = :flag
                       and ds_status in ('CUMPRIDA','PERDIDA')
                    """).param("id", clinica).param("flag", flag)
                    .query(Long.class).single();

            assertThat(linha.qtObrigacoes())
                    .as("o total do grupo %s tem que ser a contagem crua", linha.dsGrupo())
                    .isEqualTo(cruas);

            BigDecimal esperada = BigDecimal.valueOf(linha.qtCumpridas())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(linha.qtObrigacoes()), 2, RoundingMode.HALF_UP);

            assertThat(linha.pcCumprimento().doubleValue())
                    .as("a taxa e cumpridas sobre o total, arredondada")
                    .isCloseTo(esperada.doubleValue(), Offset.offset(0.01));
        }
    }

    /**
     * O sorteio mira o percentual que recebe, e a prova e na propria funcao.
     *
     * <p>Antes este caso contava os pets semeados de cada clinica e desistia
     * quando nao havia nenhum — media a amostra, e nao o sorteador. Sobre umas
     * poucas centenas de pets a proporcao oscila tanto que a faixa aceita tinha
     * que ir de 3% a 20%, o que so barrava o caso degenerado.
     *
     * <p>Chamando {@code FN_CLV_GRUPO_CONTROLE} direto sobre dez mil ids, a lei
     * dos grandes numeros aperta a faixa para um ponto percentual e o teste passa
     * a afirmar o que diz no nome. Nao depende de dado nenhum na base, e ainda
     * confere que o parametro de percentual e respeitado — o que impede alguem de
     * "ajustar" o sorteio ignorando o argumento.
     */
    @Test
    @DisplayName("o sorteio respeita o percentual que recebe")
    void sorteioRespeitaOPercentualPedido() {
        assertThat(proporcaoSorteada(10, 10_000))
                .as("o padrao do sorteio e 10%%")
                .isCloseTo(10.0, Offset.offset(1.0));

        assertThat(proporcaoSorteada(25, 10_000))
                .as("pedir 25%% tem que mudar o resultado: o parametro nao pode ser decorativo")
                .isCloseTo(25.0, Offset.offset(1.0));
    }

    /**
     * Quantos por cento de {@code quantos} ids caem no controle, segundo a funcao
     * do banco.
     *
     * <p>A funcao tem parametros de saida, e Oracle nao deixa chamar do SQL uma
     * funcao com OUT. Dai o bloco anonimo: ele roda o laco dentro do banco e
     * devolve so a contagem, que tambem evita dez mil idas e voltas.
     */
    private double proporcaoSorteada(int percentual, int quantos) {
        long[] sorteados = new long[1];

        entityManager.unwrap(Session.class).doWork(conexao -> {
            try (CallableStatement bloco = conexao.prepareCall("""
                    DECLARE
                      v_seed VARCHAR2(200);
                      v_hash VARCHAR2(64);
                      v_qtd  NUMBER := 0;
                    BEGIN
                      FOR i IN 1 .. ? LOOP
                        IF FN_CLV_GRUPO_CONTROLE(1, i, 1, ?, v_seed, v_hash) = 'S' THEN
                          v_qtd := v_qtd + 1;
                        END IF;
                      END LOOP;
                      ? := v_qtd;
                    END;
                    """)) {
                bloco.setInt(1, quantos);
                bloco.setInt(2, percentual);
                bloco.registerOutParameter(3, Types.NUMERIC);
                bloco.execute();
                sorteados[0] = bloco.getLong(3);
            }
        });

        return 100.0 * sorteados[0] / quantos;
    }

    /**
     * O caminho completo do card, incluindo a conta de valor.
     *
     * <p>Nao afirma o valor em reais — ele depende do volume, que varia. Afirma a
     * relacao: consultas atribuiveis vezes ticket da o valor, e o ticket sai das
     * consultas realizadas da clinica, e nao de constante no codigo.
     */
    @Test
    @DisplayName("consultas atribuiveis e valor saem do delta e do ticket real")
    void valorAtribuivelSaiDoDeltaEDoTicketReal() {
        // 60% no tratado contra 20% no controle: delta positivo e amostra
        // folgada, montados aqui. Antes o teste lia a primeira clinica da base e
        // desistia quando a amostra dela nao passava do piso — ou seja, desistia
        // exatamente na condicao que ele existe para exercitar.
        Long clinica = coorteFabricada(100, 60, 100, 20).clinica();
        TenantContext.set(clinica);
        CoorteResponse coorte = service.daClinicaLogada();

        assertThat(coorte.amostraSuficiente())
                .as("a coorte montada tem que passar do piso, senao o teste nao prova nada")
                .isTrue();

        assertThat(coorte.deltaPontosPercentuais().doubleValue())
                .as("o produto tem que aparecer: tratado acima do controle")
                .isPositive();

        BigDecimal ticketReal = jdbcClient.sql("""
                select round(avg(c.nr_valor), 2)
                  from TB_CLV_CONSULTA c join TB_CLV_PET p on p.id_pet = c.id_pet
                 where p.id_clinica = :id and c.nr_valor is not null
                   and c.ds_status = 'REALIZADA'
                """).param("id", clinica).query(BigDecimal.class).single();

        assertThat(coorte.vlTicketMedio())
                .as("o ticket vem das consultas realizadas da clinica")
                .isEqualByComparingTo(ticketReal);

        assertThat(coorte.vlAtribuivel())
                .as("valor e consultas atribuiveis vezes o ticket")
                .isEqualByComparingTo(ticketReal.multiply(
                        BigDecimal.valueOf(coorte.consultasAtribuiveis())));

        assertThat(coorte.consultasAtribuiveis())
                .as("nao se pode atribuir mais consultas do que o grupo tratado tem")
                .isLessThanOrEqualTo(coorte.tratado().obrigacoes());
    }

    /**
     * A guarda de amostra, provada sem depender do estado do banco.
     *
     * <p>O caso degenerado nao existe mais nesta base — e nao deve voltar a
     * existir. Entao ele e montado aqui, no proprio teste, com o piso declarado.
     */
    @Test
    @DisplayName("controle abaixo do piso de pets nao vira delta nem valor")
    void amostraPequenaNaoViraNumero() {
        assertThat(CoorteResponse.MINIMO_DE_PETS_NO_CONTROLE)
                .as("o piso e contado em pets, que e a unidade do sorteio")
                .isGreaterThan(1);

        CoorteResponse insuficiente = new CoorteResponse(
                new CoorteResponse.Grupo(300, 177, 40, BigDecimal.valueOf(59.0)),
                new CoorteResponse.Grupo(10, 8, 1, BigDecimal.valueOf(80.0)),
                BigDecimal.valueOf(-21.0), 0, BigDecimal.ZERO,
                BigDecimal.valueOf(200), false);

        assertThat(insuficiente.amostraSuficiente()).isFalse();
        assertThat(insuficiente.consultasAtribuiveis())
                .as("sem amostra nao se atribui consulta nenhuma")
                .isZero();
        assertThat(insuficiente.vlAtribuivel())
                .as("nem valor: numero em reais tirado de um pet parece medido e nao e")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    private List<PainelCoorteService.Linha> linhasDa(Long clinica) {
        return jdbcClient.sql(SQL_LINHAS).param("id", clinica)
                .query(PainelCoorteService.Linha.class).list();
    }

    /**
     * A primeira clinica da base semeada, para o unico caso que precisa dela.
     *
     * <p>Falha, e nao pula, quando nao ha nenhuma: a mensagem diz o que rodar.
     */
    private Long primeiraClinicaSemeada() {
        List<Long> clinicas = jdbcClient
                .sql("select id_clinica from TB_CLV_CLINICA order by id_clinica")
                .query(Long.class).list();

        assertThat(clinicas)
                .as("este caso confere a calibragem do seed e precisa da base de "
                        + "demonstracao: rode PR_CLV_SEED_EXECUTAR antes")
                .isNotEmpty();
        return clinicas.getFirst();
    }

    /**
     * Uma clinica com a coorte que o teste pedir, e nada mais.
     *
     * <p>Cada obrigacao vai para um pet proprio, porque {@code qt_pets} da view
     * conta pets distintos — e o piso de amostra do card olha justamente para
     * ele. Uma consulta realizada por pet da o ticket medio, sem o qual o valor
     * em reais seria zero por outro motivo que nao o que se quer testar.
     */
    private CenarioClinico.Cenario coorteFabricada(int tratadas, int cumpridasTratado,
                                                   int controles, int cumpridasControle) {
        CenarioClinico.Cenario base = fixture.clinicaCompleta("Clinica da coorte");

        materializar(base, tratadas, cumpridasTratado, false);
        materializar(base, controles, cumpridasControle, true);

        entityManager.flush();
        entityManager.clear();
        return base;
    }

    private void materializar(CenarioClinico.Cenario base, int quantas, int cumpridas,
                              boolean grupoControle) {
        for (int i = 0; i < quantas; i++) {
            Long pet = fixture.novoPet(base.clinica(), base.tutor());
            fixture.novaConsulta(pet, base.veterinario());
            fixture.novaObrigacao(base.clinica(), pet,
                    i < cumpridas ? "CUMPRIDA" : "PERDIDA", -30, grupoControle);
        }
    }

    /**
     * A taxa alvo de um dos parametros de {@code PR_CLV_SEED_DESFECHOS}, em
     * pontos percentuais.
     *
     * <p>Falha se a procedure nao estiver no schema. Antes pulava — mas a
     * procedure nasce na V8 e existe em qualquer schema migrado, entao a ausencia
     * dela significa migration faltando, que e um defeito para mostrar e nao para
     * contornar.
     */
    private double taxaDoSeed(String parametro) {
        java.util.Optional<String> literal = jdbcClient.sql(SQL_TAXA_DO_SEED)
                .param("parametro", parametro)
                .query(String.class)
                .optional();

        assertThat(literal)
                .as("PR_CLV_SEED_DESFECHOS nasce na V8: sem ela, o schema esta incompleto")
                .isPresent();

        // a procedure guarda a fracao (0,58); a view devolve pontos percentuais
        return new BigDecimal(literal.get().trim())
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
