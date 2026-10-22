package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.enums.EventoGatilho;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.exception.ConflitoDeEstadoException;
import br.com.clyvovet.server.support.CenarioClinico;
import br.com.clyvovet.server.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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
 *
 * <p><b>A clinica e o pet sao fabricados aqui.</b> A classe pegava a primeira
 * clinica que existisse e, nos testes de transicao, a primeira obrigacao PREVISTA
 * que achasse — desistindo por {@code assumeTrue} quando nao achava. Com a
 * varredura de lembretes rodando todo dia, "a primeira PREVISTA" e um alvo que se
 * move sozinho: obrigacoes saem desse estado sem ninguem tocar no teste. Fabricar
 * remove a dependencia e, de quebra, torna as contagens deterministicas.
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

    /** Horizonte pedido ao motor: um ano cobre a serie vacinal inteira. */
    private static final int HORIZONTE_DE_UM_ANO = 365;

    private CenarioClinico fixture;
    private CenarioClinico.Cenario cenario;
    private Long clinica;

    @BeforeEach
    void montarClinica() {
        fixture = new CenarioClinico(entityManager);
        cenario = fixture.clinicaCompleta("Clinica do motor");
        clinica = cenario.clinica();
        TenantContext.set(clinica);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    /**
     * Registrar o gatilho de uma consulta materializa as obrigacoes do protocolo.
     *
     * <p><b>O teste cria o proprio pet, e essa e a correcao que importa.</b> Antes
     * ele varria consultas ja existentes de pets que ja tinham obrigacoes — ou
     * seja, exatamente os pets que o motor se recusa a reprocessar. A guarda de
     * cobertura de {@code PR_CLV_GERAR_OBRIGACOES} nao regera protocolo que ja
     * tem obrigacao viva e futura, entao numa base ja exercitada as vinte
     * candidatas devolviam zero e o teste falhava sem que nada estivesse
     * quebrado. Passava ou nao conforme o quanto a base tinha sido usada.
     *
     * <p>Um filhote recem-cadastrado nao tem cobertura nenhuma, e casa com a
     * regra de {@code CAN_V10_SERIE} — canina, entre 1,5 e 4 meses. O gatilho
     * entao tem obrigatoriamente o que gerar, em qualquer base com o catalogo
     * clinico da V9 aplicado. Hoje a clinica que o abriga tambem e do teste,
     * entao a contagem de obrigacoes da clinica comeca em zero e o delta medido
     * abaixo e exatamente o que este gatilho produziu.
     *
     * <p>A assercao que importa nao e o numero em si, e sim que o contador
     * devolvido pelo parametro de saida bate com as linhas realmente gravadas.
     * Contador e linhas divergindo significaria que a aplicacao esta lendo o OUT
     * errado — e a API devolveria uma quantidade que nunca existiu.
     */
    @Test
    void gatilhoDeConsultaMaterializaObrigacoes() {
        Long consulta = consultaDeFilhoteNovo();

        long antes = contarObrigacoes();

        GerarObrigacoesResponse resposta = service.gerarParaConsulta(consulta,
                new GerarObrigacoesRequest(EventoGatilho.CONSULTA_REGISTRADA,
                        LocalDate.now(), "teste de integracao", HORIZONTE_DE_UM_ANO));

        assertThat(resposta.qtdCriadas())
                .as("um filhote sem cobertura tem que fazer o motor materializar a serie vacinal")
                .isPositive();
        assertThat(resposta.correlationId()).isNotBlank();

        assertThat(contarNativo("select count(*) from TB_CLV_OBRIGACAO where ds_correlation_id = '%s'"
                .formatted(resposta.correlationId())))
                .as("o contador do parametro de saida tem que bater com as linhas gravadas")
                .isEqualTo(resposta.qtdCriadas());

        assertThat(contarObrigacoes() - antes)
                .as("e o total tem que crescer exatamente pelo que foi criado")
                .isEqualTo(resposta.qtdCriadas());
    }

    /**
     * Um filhote canino de dois meses, com consulta de hoje, criado aqui.
     *
     * <p>Dois meses porque e a faixa que {@code CAN_V10_SERIE} exige — o que
     * precisa ser novo e o <b>pet</b>, porque e nele que mora a cobertura do
     * motor. Raca vem do catalogo global; tutor e veterinario, da clinica que
     * este teste montou.
     */
    private Long consultaDeFilhoteNovo() {
        Long filhote = fixture.novoPet(clinica, cenario.tutor(), 2);
        return fixture.novaConsulta(filhote, cenario.veterinario());
    }

    /**
     * PREVISTA -> CUMPRIDA e um salto ilegal: o pet nao pode ser atendido sem
     * antes ter sido agendado. Quem recusa e a procedure, com ORA-20010.
     */
    @Test
    void transicaoInvalidaERejeitadaSemAlterarNada() {
        Long obrigacaoId = umaObrigacaoPrevista();

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
        Long obrigacaoId = umaObrigacaoPrevista();

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

    /**
     * Uma obrigacao PREVISTA deste teste, vencendo daqui a 30 dias.
     *
     * <p>Fabricada, e nao a {@code min(id_obrigacao)} PREVISTA da base: com a
     * varredura de lembretes levando obrigacoes a NOTIFICADA todo dia, aquela
     * consulta devolvia uma linha diferente a cada execucao e podia nao devolver
     * nenhuma — e ai os dois testes de transicao desistiam em silencio.
     */
    private Long umaObrigacaoPrevista() {
        return fixture.novaObrigacao(clinica, cenario.pet(), "PREVISTA", 30, false);
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
