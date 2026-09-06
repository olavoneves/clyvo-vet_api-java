package br.com.clyvovet.server.painel;

import br.com.clyvovet.server.tenant.TenantContext;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
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
 * sorteio de {@code PR_CLV_SEED_DESFECHOS} (0,58 tratado e 0,36 controle); a
 * <b>aritmetica</b> da view, conferida contra a tabela que ela resume; e a
 * <b>forma</b> — um grupo de cada por clinica, o controle perto dos 10% dos
 * pets, e o tratado acima do controle.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle configurado nas variaveis de ambiente")
class PainelCoorteIntegracaoTest {

    /** Tolerancia das taxas: o sorteio e aleatorio, a media e que e estavel. */
    private static final double FOLGA_PP = 6.0;

    private static final double TAXA_TRATADO_SEED = 58.0;
    private static final double TAXA_CONTROLE_SEED = 36.0;

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

    private List<Long> clinicas;

    @BeforeEach
    void carregarClinicas() {
        clinicas = jdbcClient.sql("select id_clinica from TB_CLV_CLINICA order by id_clinica")
                .query(Long.class).list();
        Assumptions.assumeFalse(clinicas.isEmpty(), "nenhuma clinica cadastrada");
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a view devolve um grupo tratado e um controle por clinica, com a taxa do sorteio")
    void viewDevolveOsDoisGruposComATaxaDoSorteio() {
        List<PainelCoorteService.Linha> linhas = linhasDa(clinicas.getFirst());

        assertThat(linhas)
                .as("uma linha por grupo: tratado e controle")
                .hasSize(2)
                .extracting(PainelCoorteService.Linha::dsGrupo)
                .containsExactlyInAnyOrder("TRATADO", "CONTROLE");

        for (PainelCoorteService.Linha linha : linhas) {
            double esperada = "TRATADO".equals(linha.dsGrupo())
                    ? TAXA_TRATADO_SEED : TAXA_CONTROLE_SEED;

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

    /** A aritmetica da view, conferida contra a tabela que ela resume. */
    @Test
    @DisplayName("a taxa da view bate com a contagem crua da tabela")
    void taxaDaViewBateComATabela() {
        Long clinica = clinicas.getFirst();

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
     * O sorteio mira 10% dos pets.
     *
     * <p>A folga e larga de proposito: com poucas centenas de pets, 10% oscila.
     * O que este teste barra e o caso degenerado que ja aconteceu de verdade —
     * o controle com um pet so, que fazia a base local afirmar que o produto
     * derrubava o comparecimento em vinte pontos.
     */
    @Test
    @DisplayName("o grupo de controle fica perto dos 10% dos pets")
    void controleFicaPertoDeDezPorCentoDosPets() {
        for (Long clinica : clinicas) {
            TenantContext.set(clinica);
            CoorteResponse coorte = service.daClinicaLogada();

            long pets = coorte.tratado().pets() + coorte.controle().pets();
            Assumptions.assumeTrue(pets > 0, "clinica sem pet com obrigacao resolvida");

            double proporcao = 100.0 * coorte.controle().pets() / pets;
            assertThat(proporcao)
                    .as("controle da clinica %s: %s de %s pets",
                            clinica, coorte.controle().pets(), pets)
                    .isBetween(3.0, 20.0);
        }
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
        Long clinica = clinicas.getFirst();
        TenantContext.set(clinica);
        CoorteResponse coorte = service.daClinicaLogada();

        Assumptions.assumeTrue(coorte.amostraSuficiente(),
                "amostra de controle insuficiente nesta base");

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
}
