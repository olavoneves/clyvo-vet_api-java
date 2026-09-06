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
     * clinico da V9 aplicado.
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
     * Um filhote canino novo, com consulta de hoje, criado aqui e nao no seed.
     *
     * <p>Reaproveita tutor, raca e veterinario que ja existem — o que precisa ser
     * novo e o <b>pet</b>, porque e nele que mora a cobertura do motor. Roda
     * dentro da transacao do teste, entao nada disso sobra na base.
     */
    private Long consultaDeFilhoteNovo() {
        Long racaCanina = idNativo("""
                select min(r.id_raca) from TB_CLV_RACA r
                 join TB_CLV_ESPECIE e on e.id_especie = r.id_especie
                where upper(e.nm_especie) = 'CANINA'
                """);
        Long tutor = idNativo(
                "select min(id_tutor) from TB_CLV_TUTOR where id_clinica = %d".formatted(clinica));
        Long veterinario = idNativo(
                "select min(id_veterinario) from TB_CLV_VETERINARIO where id_clinica = %d"
                        .formatted(clinica));

        Assumptions.assumeTrue(racaCanina != null && tutor != null && veterinario != null,
                "a clinica precisa de raca canina, tutor e veterinario cadastrados");

        entityManager.createNativeQuery("""
                insert into TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, ds_porte,
                                        ds_status_pet, id_tutor, id_raca, id_clinica)
                values ('Filhote do teste', ADD_MONTHS(TRUNC(SYSDATE), -2), 'M', 'MEDIO',
                        'ATIVO', %d, %d, %d)
                """.formatted(tutor, racaCanina, clinica)).executeUpdate();

        Long pet = idNativo("""
                select max(id_pet) from TB_CLV_PET
                 where nm_pet = 'Filhote do teste' and id_clinica = %d
                """.formatted(clinica));

        entityManager.createNativeQuery("""
                insert into TB_CLV_CONSULTA (dt_consulta, ds_motivo, ds_status, nr_valor,
                                             id_pet, id_veterinario)
                values (TRUNC(SYSDATE), 'Primeira consulta do filhote', 'REALIZADA', 180,
                        %d, %d)
                """.formatted(pet, veterinario)).executeUpdate();

        entityManager.flush();

        return idNativo("""
                select max(id_consulta) from TB_CLV_CONSULTA where id_pet = %d
                """.formatted(pet));
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
