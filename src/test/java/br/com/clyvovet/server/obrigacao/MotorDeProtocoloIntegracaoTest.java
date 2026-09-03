package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.enums.EventoGatilho;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.exception.ConflitoDeEstadoException;
import br.com.clyvovet.server.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O contrato entre a aplicacao e o motor de protocolo em PL/SQL.
 *
 * <p>Nada aqui testa a regra de negocio — ela e do banco e tem os proprios
 * testes em PL/SQL. O que esta sob prova e a fronteira: nome e ordem dos
 * parametros, tipo de cada um, leitura do parametro de saida, participacao na
 * mesma transacao e a traducao de ORA-20010 em erro de dominio. E exatamente
 * essa fronteira que quebra em silencio quando alguem mexe na procedure.
 *
 * <p>Tudo roda dentro da transacao do teste e e desfeito no fim. As procedures
 * nao dao COMMIT proprio nem usam transacao autonoma, entao o rollback alcanca
 * as linhas que elas escreveram.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle com o motor de protocolo instalado")
class MotorDeProtocoloIntegracaoTest {

    @Autowired
    private ObrigacaoService service;

    @Autowired
    private ObrigacaoRepository repository;

    @Autowired
    private ObrigacaoTransicaoRepository transicaoRepository;

    @Autowired
    private EntityManager entityManager;

    /** Ate quantas consultas tentar antes de desistir de achar uma que gere. */
    private static final int CANDIDATAS_A_VARRER = 20;

    private static final int HORIZONTE_DE_UM_ANO = 365;

    private Long clinica;

    @BeforeEach
    void escolherClinica() {
        List<Long> clinicas = entityManager
                .createNativeQuery("select id_clinica from TB_CLV_CLINICA order by id_clinica", Long.class)
                .getResultList();
        Assumptions.assumeFalse(clinicas.isEmpty(), "nenhuma clinica cadastrada");

        clinica = clinicas.get(0);
        TenantContext.set(clinica);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    /**
     * Registrar o gatilho de uma consulta materializa as obrigacoes do protocolo.
     *
     * <p>Nem toda consulta gera algo: o motor so materializa quando o pet casa
     * com as regras de ativacao do protocolo — especie, fase de vida, peso,
     * condicoes. Por isso o teste varre candidatos ate encontrar um que gere,
     * em vez de apostar numa consulta especifica e virar um teste intermitente.
     *
     * <p>A asserção que importa nao e o numero em si, e sim que o contador
     * devolvido pelo parametro de saida bate com as linhas realmente gravadas.
     * Contador e linhas divergindo significaria que a aplicacao esta lendo o OUT
     * errado — e a API devolveria uma quantidade que nunca existiu.
     */
    @Test
    void gatilhoDeConsultaMaterializaObrigacoes() {
        List<Long> candidatas = consultasDePetsComProtocolo();
        Assumptions.assumeFalse(candidatas.isEmpty(),
                "a clinica nao tem consultas de pets alcancados por protocolo");

        long antes = 0;
        GerarObrigacoesResponse resposta = null;

        for (Long consultaId : candidatas) {
            antes = contarObrigacoes();
            resposta = service.gerarParaConsulta(consultaId,
                    new GerarObrigacoesRequest(EventoGatilho.CONSULTA_REGISTRADA,
                            LocalDate.now(), "teste de integracao", HORIZONTE_DE_UM_ANO));
            if (resposta.qtdCriadas() > 0) {
                break;
            }
        }

        assertThat(resposta).isNotNull();
        assertThat(resposta.qtdCriadas())
                .as("nenhuma das %d consultas candidatas gerou obrigacao: "
                        + "ou o catalogo de protocolos esta vazio, ou o motor parou de casar regra",
                        candidatas.size())
                .isPositive();
        assertThat(resposta.correlationId()).isNotBlank();

        assertThat(contarNativo("select count(*) from TB_CLV_OBRIGACAO where ds_correlation_id = '%s'"
                .formatted(resposta.correlationId())))
                .as("o contador do parametro de saida tem que bater com as linhas gravadas")
                .isEqualTo(resposta.qtdCriadas());

        assertThat(contarObrigacoes()).isEqualTo(antes + resposta.qtdCriadas());

        // toda obrigacao nasce na clinica do token, nunca na do corpo da requisicao
        assertThat(contarNativo("""
                select count(*) from TB_CLV_OBRIGACAO
                 where ds_correlation_id = '%s' and id_clinica = %d
                """.formatted(resposta.correlationId(), clinica)))
                .isEqualTo(resposta.qtdCriadas());
    }

    /**
     * Consultas de pets que ja foram alcancados por algum protocolo alguma vez.
     * E o recorte mais barato que aumenta muito a chance de o gatilho produzir
     * algo — pet que nunca casou com regra nenhuma tende a nao casar agora.
     */
    private List<Long> consultasDePetsComProtocolo() {
        return entityManager.createNativeQuery("""
                select c.id_consulta from TB_CLV_CONSULTA c
                 join TB_CLV_PET p on p.id_pet = c.id_pet
                where p.id_clinica = %d
                  and exists (select 1 from TB_CLV_OBRIGACAO o where o.id_pet = c.id_pet)
                  and rownum <= %d
                """.formatted(clinica, CANDIDATAS_A_VARRER), Long.class)
                .getResultList();
    }

    /**
     * PREVISTA -> CUMPRIDA e um salto ilegal: o pet nao pode ser atendido sem
     * antes ter sido agendado. Quem recusa e a procedure, com ORA-20010.
     */
    @Test
    void transicaoInvalidaERejeitadaSemAlterarNada() {
        Long obrigacaoId = idNativo("""
                select min(id_obrigacao) from TB_CLV_OBRIGACAO
                 where id_clinica = %d and ds_status = 'PREVISTA'
                """.formatted(clinica));
        Assumptions.assumeTrue(obrigacaoId != null, "nenhuma obrigacao PREVISTA para testar");

        long transicoesAntes = transicaoRepository.findByObrigacaoIdOrderByDtOcorrenciaAsc(obrigacaoId).size();

        assertThatThrownBy(() -> service.transitar(obrigacaoId,
                new TransicaoRequest(ObrigacaoStatus.CUMPRIDA, "salto ilegal", null, null, null)))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("Transicao invalida")
                .hasMessageContaining("PREVISTA");

        entityManager.clear();

        assertThat(repository.findById(obrigacaoId))
                .get()
                .extracting(Obrigacao::getDsStatus)
                .as("recusada a transicao, o estado anterior tem que continuar de pe")
                .isEqualTo(ObrigacaoStatus.PREVISTA);
        assertThat(transicaoRepository.findByObrigacaoIdOrderByDtOcorrenciaAsc(obrigacaoId))
                .as("transicao recusada nao pode deixar rastro na linha do tempo")
                .hasSize((int) transicoesAntes);
    }

    /**
     * O caminho legal, para que o teste anterior signifique alguma coisa: sem
     * este, uma procedure que recusasse tudo passaria como se estivesse certa.
     */
    @Test
    void transicaoValidaAvancaOEstadoERegistraALinhaDoTempo() {
        Long obrigacaoId = idNativo("""
                select min(id_obrigacao) from TB_CLV_OBRIGACAO
                 where id_clinica = %d and ds_status = 'PREVISTA'
                """.formatted(clinica));
        Assumptions.assumeTrue(obrigacaoId != null, "nenhuma obrigacao PREVISTA para testar");

        int transicoesAntes = transicaoRepository
                .findByObrigacaoIdOrderByDtOcorrenciaAsc(obrigacaoId).size();

        ObrigacaoDetalheResponse detalhe = service.transitar(obrigacaoId,
                new TransicaoRequest(ObrigacaoStatus.NOTIFICADA, "lembrete enviado", null, null, null));

        assertThat(detalhe.obrigacao().status()).isEqualTo(ObrigacaoStatus.NOTIFICADA);
        assertThat(detalhe.dtNotificada())
                .as("a procedure carimba a data da etapa alcancada")
                .isNotNull();
        assertThat(detalhe.transicoes()).hasSize(transicoesAntes + 1);

        // a mesma transacao que mudou o status publicou o evento no outbox
        assertThat(contarNativo("""
                select count(*) from TB_CLV_OUTBOX_EVENT
                 where ds_agregado = 'OBRIGACAO' and nr_id_agregado = %d
                   and ds_event_type = 'ObrigacaoNotificada'
                """.formatted(obrigacaoId)))
                .isPositive();
    }

    private long contarObrigacoes() {
        return contarNativo("select count(*) from TB_CLV_OBRIGACAO where id_clinica = " + clinica);
    }

    private long contarNativo(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private Long idNativo(String sql) {
        Object resultado = entityManager.createNativeQuery(sql).getSingleResult();
        return resultado == null ? null : ((Number) resultado).longValue();
    }
}
