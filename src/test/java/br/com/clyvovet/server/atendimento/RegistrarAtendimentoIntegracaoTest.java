package br.com.clyvovet.server.atendimento;

import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.auth.ClinicaUserDetails;
import br.com.clyvovet.server.enums.TipoUsuario;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Registrar atendimento" de ponta a ponta: formulario enviado, consulta e
 * obrigacoes no banco.
 *
 * <p>Vai pela cadeia de filtros das paginas, com sessao de veterinario e token
 * CSRF, e o tenant sai do principal pelo {@code TenantFilter} — como no
 * navegador. Chamar o servico direto provaria menos.
 *
 * <p>O pet e um filhote canino de dois meses: casa com {@code CAN_V10_SERIE}, e
 * o motor tem obrigatoriamente o que gerar. Com um adulto, zero obrigacoes
 * seria resposta legitima e o teste nao distinguiria motor que nao rodou de
 * motor que nao achou protocolo.
 *
 * <p>Roda dentro da transacao do teste e e desfeito no fim, inclusive o que o
 * motor gravou: {@code GERAR_OBRIGACOES} nao da COMMIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle configurado nas variaveis de ambiente")
class RegistrarAtendimentoIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CatalogoDeContextos catalogoDeContextos;

    private CenarioClinico fixture;
    private Long clinica;
    private Long veterinario;
    private Long filhote;

    @BeforeEach
    void montarFilhoteSemCobertura() {
        fixture = new CenarioClinico(entityManager);
        clinica = fixture.novaClinica("Clinica do atendimento pela tela");
        Long tutor = fixture.novoTutor(clinica);
        veterinario = fixture.novoVeterinario(clinica);
        filhote = fixture.novoPet(clinica, tutor, 2);
        entityManager.flush();
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("o formulario cria a consulta e as obrigacoes que o motor gera para ela")
    void formularioCriaConsultaEObrigacoes() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/pets/" + filhote + "/atendimentos")
                        .with(user(sessaoDoVeterinario()))
                        .with(csrf())
                        .param("veterinarioId", veterinario.toString())
                        .param("dtConsulta", LocalDate.now().toString())
                        .param("motivo", "Primeira consulta")
                        .param("diagnostico", "Saudavel")
                        .param("nrValor", "180.00")
                        .param("eventoGatilho", "CONSULTA_REGISTRADA")
                        .param("contexto", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pets/" + filhote))
                .andReturn();

        AtendimentoRegistrado registrado =
                (AtendimentoRegistrado) resultado.getFlashMap().get("atendimentoRegistrado");
        assertThat(registrado).isNotNull();
        assertThat(registrado.qtdObrigacoes())
                .as("um filhote sem cobertura tem que fazer o motor materializar a serie vacinal")
                .isPositive();

        Object[] consulta = (Object[]) entityManager.createNativeQuery("""
                        select id_consulta, ds_status, nr_valor, id_veterinario
                          from TB_CLV_CONSULTA where id_pet = :pet
                        """)
                .setParameter("pet", filhote)
                .getSingleResult();
        assertThat(((Number) consulta[0]).longValue()).isEqualTo(registrado.consultaId());
        assertThat(consulta[1]).isEqualTo("REALIZADA");
        assertThat(new BigDecimal(consulta[2].toString())).isEqualByComparingTo("180.00");
        assertThat(((Number) consulta[3]).longValue()).isEqualTo(veterinario);

        @SuppressWarnings("unchecked")
        List<Object[]> obrigacoes = entityManager.createNativeQuery("""
                        select id_pet, id_clinica, dt_prevista
                          from TB_CLV_OBRIGACAO where id_consulta_origem = :consulta
                         order by dt_prevista
                        """)
                .setParameter("consulta", registrado.consultaId())
                .getResultList();
        assertThat(obrigacoes).hasSize(registrado.qtdObrigacoes());
        assertThat(obrigacoes).allSatisfy(o -> {
            assertThat(((Number) o[0]).longValue()).isEqualTo(filhote);
            assertThat(((Number) o[1]).longValue()).isEqualTo(clinica);
        });

        // as datas da confirmacao sao as primeiras das obrigacoes que nasceram
        assertThat(registrado.proximasDatas()).isNotEmpty().isSorted();
        assertThat(registrado.proximasDatas()).allSatisfy(
                data -> assertThat(data).isAfterOrEqualTo(LocalDate.now()));
    }

    @Test
    @DisplayName("formulario invalido nao grava nada")
    void formularioInvalidoNaoGrava() throws Exception {
        mockMvc.perform(post("/pets/" + filhote + "/atendimentos")
                        .with(user(sessaoDoVeterinario()))
                        .with(csrf())
                        .param("dtConsulta", LocalDate.now().toString())
                        .param("eventoGatilho", "CONSULTA_REGISTRADA"))
                .andExpect(status().isOk());

        Number consultas = (Number) entityManager.createNativeQuery(
                        "select count(*) from TB_CLV_CONSULTA where id_pet = :pet")
                .setParameter("pet", filhote)
                .getSingleResult();
        assertThat(consultas.longValue()).isZero();
    }

    /**
     * O select oferece o que o motor le: cada codigo do catalogo esta na lista
     * de alguma condicao PROCEDIMENTO do mesmo gatilho. Nenhum codigo fixo aqui
     * — o teste confere a tela contra o banco, e nao contra uma lista copiada.
     */
    @Test
    @DisplayName("os contextos do select saem das condicoes PROCEDIMENTO do catalogo")
    void contextosSaemDoCatalogo() {
        TenantContext.set(clinica);
        var porGatilho = catalogoDeContextos.porGatilho();

        assertThat(porGatilho)
                .as("o catalogo da V9 tem ao menos uma regra que le o procedimento")
                .isNotEmpty();
        porGatilho.forEach((gatilho, opcoes) -> assertThat(opcoes).allSatisfy(opcao -> {
            Number casa = (Number) entityManager.createNativeQuery("""
                            select count(*) from TB_CLV_CONDICAO_PROT c
                              join TB_CLV_REGRA_ATIVACAO r on r.id_regra_ativacao = c.id_regra_ativacao
                             where c.ds_atributo = 'PROCEDIMENTO'
                               and r.ds_evento_gatilho = :gatilho
                               and instr(',' || upper(c.ds_valor_texto) || ',', ',' || :codigo || ',') > 0
                            """)
                    .setParameter("gatilho", gatilho.name())
                    .setParameter("codigo", opcao.codigo())
                    .getSingleResult();
            assertThat(casa.longValue()).as("%s em %s", opcao.codigo(), gatilho).isPositive();
            assertThat(opcao.rotulo()).isNotBlank();
        }));
    }

    /**
     * O caso da Bela: procedimento realizado num adulto. Sem contexto, zero;
     * com o primeiro contexto que o select oferece, o motor gera.
     */
    @Test
    @DisplayName("procedimento realizado com contexto do catalogo gera obrigacoes num adulto")
    void contextoDoCatalogoFazOMotorGerar() throws Exception {
        TenantContext.set(clinica);
        String codigo = catalogoDeContextos.porGatilho()
                .get(br.com.clyvovet.server.enums.EventoGatilho.PROCEDIMENTO_REALIZADO)
                .getFirst().codigo();
        TenantContext.clear();
        Long adulto = fixture.novoPet(clinica, fixture.novoTutor(clinica));
        entityManager.flush();

        AtendimentoRegistrado semContexto = registrar(adulto, "");
        assertThat(semContexto.qtdObrigacoes()).isZero();

        AtendimentoRegistrado comContexto = registrar(adulto, codigo);
        assertThat(comContexto.qtdObrigacoes())
                .as("contexto %s oferecido pelo select tem que ativar alguma regra", codigo)
                .isPositive();
    }

    private AtendimentoRegistrado registrar(Long pet, String contexto) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/pets/" + pet + "/atendimentos")
                        .with(user(sessaoDoVeterinario()))
                        .with(csrf())
                        .param("veterinarioId", veterinario.toString())
                        .param("dtConsulta", LocalDate.now().toString())
                        .param("eventoGatilho", "PROCEDIMENTO_REALIZADO")
                        .param("contexto", contexto))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (AtendimentoRegistrado) resultado.getFlashMap().get("atendimentoRegistrado");
    }

    private ClinicaUserDetails sessaoDoVeterinario() {
        return new ClinicaUserDetails(
                new AuthenticatedUser(veterinario, "vet@teste.local", "Vet de teste",
                        TipoUsuario.VETERINARIO, clinica),
                "irrelevante");
    }
}
