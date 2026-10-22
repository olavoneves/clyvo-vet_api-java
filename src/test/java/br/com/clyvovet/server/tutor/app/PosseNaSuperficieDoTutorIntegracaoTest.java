package br.com.clyvovet.server.tutor.app;

import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.pet.PetResponse;
import br.com.clyvovet.server.pet.PetService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que esta rodada existe para impedir: um tutor alcancando o pet de outro
 * tutor da <b>mesma</b> clinica.
 *
 * <p>O isolamento entre clinicas ja tinha teste e nunca esteve em jogo. O que
 * nao existia era isolamento dentro de uma clinica: as rotas de gestao aceitavam
 * o id do dono pela URL e pelo corpo, e ninguem conferia. Por isso o cenario
 * daqui tem <b>uma</b> clinica e <b>dois</b> tutores — duas clinicas testariam
 * de novo a barreira que ja funciona e deixariam esta passar.
 *
 * <p>Vai pela cadeia de filtros inteira, com token emitido pelo
 * {@code JwtService} de verdade: o que esta sob teste e a resposta HTTP que o
 * aplicativo mobile vai receber, e ela depende tanto da {@code SecurityConfig}
 * quanto do {@link PosseDoTutor}. Chamar o controller direto provaria menos.
 *
 * <p><b>404, e nao 403.</b> Um 403 confirmaria que o recurso existe e deixaria
 * enumerar ids pela diferenca entre as duas respostas. Cada teste que espera 404
 * tem ao lado o mesmo pedido sobre o proprio recurso, devolvendo 200: sem esse
 * par, um 404 por rota quebrada passaria por seguranca.
 *
 * <p>Roda dentro da transacao do teste e e desfeito ao final — inclusive o que
 * as requisicoes escreveram, porque elas correm na mesma thread e na mesma
 * transacao.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle configurado nas variaveis de ambiente")
class PosseNaSuperficieDoTutorIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PetService petService;

    private CenarioClinico fixture;

    private Long clinica;
    private Long tutorA;
    private Long tutorB;
    private Long petDeA;
    private Long petDeB;
    private Long veterinario;
    private Long raca;

    private String tokenDeA;
    private String tokenDeB;

    /**
     * Uma clinica, dois tutores, um pet para cada.
     *
     * <p>Montado por consulta nativa, que nao passa pelo filtro de tenant, e sem
     * tocar em repositorio: o tenant de cada requisicao vem do token, posto pelo
     * {@code TenantFilter}, e nao do que o teste tenha deixado no ThreadLocal.
     */
    @BeforeEach
    void montarDoisTutoresNaMesmaClinica() {
        fixture = new CenarioClinico(entityManager);

        clinica = fixture.novaClinica("Clinica do app do tutor");
        tutorA = fixture.novoTutor(clinica);
        tutorB = fixture.novoTutor(clinica);
        veterinario = fixture.novoVeterinario(clinica);
        petDeA = fixture.novoPet(clinica, tutorA);
        petDeB = fixture.novoPet(clinica, tutorB);
        raca = fixture.id("""
                select min(r.id_raca) from TB_CLV_RACA r
                  join TB_CLV_ESPECIE e on e.id_especie = r.id_especie
                 where upper(e.nm_especie) = 'CANINA'
                """);
        entityManager.flush();

        tokenDeA = bearer(tutorA, "a");
        tokenDeB = bearer(tutorB, "b");
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    // ---------- pets ----------

    @Test
    @DisplayName("o tutor le o proprio pet e nao o do outro: 200 contra 404")
    void naoLeOPetDoOutro() throws Exception {
        mockMvc.perform(get("/api/tutor/pets/" + petDeA).header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(petDeA))
                .andExpect(jsonPath("$.tutorId").value(tutorA));

        mockMvc.perform(get("/api/tutor/pets/" + petDeB).header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("o tutor nao altera o pet do outro")
    void naoAlteraOPetDoOutro() throws Exception {
        mockMvc.perform(put("/api/tutor/pets/" + petDeB)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDePet("Sequestrado")))
                .andExpect(status().isNotFound());

        assertThat(contar("""
                select count(*) from TB_CLV_PET where id_pet = %d and nm_pet = 'Sequestrado'
                """.formatted(petDeB)))
                .as("o PUT recusado nao pode ter alterado nada")
                .isZero();

        // e o proprio pet continua editavel: o 404 acima e de posse, nao de rota
        mockMvc.perform(put("/api/tutor/pets/" + petDeA)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDePet("Rex renomeado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Rex renomeado"))
                .andExpect(jsonPath("$.tutorId").value(tutorA));
    }

    @Test
    @DisplayName("o tutor nao remove o pet do outro")
    void naoRemoveOPetDoOutro() throws Exception {
        mockMvc.perform(delete("/api/tutor/pets/" + petDeB)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isNotFound());

        assertThat(contar("select count(*) from TB_CLV_PET where id_pet = %d".formatted(petDeB)))
                .as("o pet do outro tutor tem que continuar existindo")
                .isEqualTo(1);

        mockMvc.perform(delete("/api/tutor/pets/" + petDeA)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isNoContent());
    }

    /**
     * O caso que o {@code PetDoTutorRequest} existe para tornar impossivel: o
     * corpo carrega o tutor errado e o vinculo sai do token assim mesmo.
     */
    @Test
    @DisplayName("o vinculo do pet criado ignora o que vier no corpo")
    void naoCriaPetParaOutroTutor() throws Exception {
        String corpoComTutorAlheio = """
                {"nome":"Bidu","sexo":"M","porte":"MEDIO","status":"ATIVO",
                 "racaId":%d,"tutorId":%d}
                """.formatted(raca, tutorB);

        MvcResult resultado = mockMvc.perform(post("/api/tutor/pets")
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComTutorAlheio))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tutorId").value(tutorA))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .as("o tutorId do corpo nao pode ter chegado ao banco")
                .doesNotContain("\"tutorId\":" + tutorB);
    }

    /**
     * A remocao do aplicativo nao pode custar o prontuario.
     *
     * <p>Este teste so passa porque o DELETE do tutor inativa. Com a remocao
     * fisica que estava aqui antes, um pet com consulta e obrigacao nem chegava
     * a ser removido: as FKs de TB_CLV_PET nao tem ON DELETE CASCADE e o Oracle
     * recusava com ORA-02292, que a aplicacao traduzia em 409. O historico nunca
     * correu risco — o botao e que nao funcionava em nenhum pet de verdade.
     *
     * <p>A obrigacao e a parte que importa: e dela que sai a taxa de cumprimento
     * do painel de coorte. Um pet removido pelo tutor tem que continuar contando.
     */
    @Test
    @DisplayName("remover o pet no aplicativo o esconde do tutor e preserva o historico")
    void removerInativaEPreservaOHistorico() throws Exception {
        Long consulta = fixture.novaConsulta(petDeA, veterinario);
        Long obrigacao = fixture.novaObrigacao(clinica, petDeA, "PREVISTA", 30, false);
        entityManager.flush();

        mockMvc.perform(delete("/api/tutor/pets/" + petDeA)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tutor/pets").header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + petDeA + ")]").doesNotExist());

        assertThat(contar("""
                select count(*) from TB_CLV_PET
                 where id_pet = %d and ds_status_pet = 'INATIVO'
                """.formatted(petDeA)))
                .as("o pet tem que continuar existindo, inativado")
                .isEqualTo(1);

        assertThat(contar("select count(*) from TB_CLV_CONSULTA where id_consulta = %d"
                .formatted(consulta)))
                .as("a consulta do pet removido nao pode sumir")
                .isEqualTo(1);

        assertThat(contar("select count(*) from TB_CLV_OBRIGACAO where id_obrigacao = %d"
                .formatted(obrigacao)))
                .as("a obrigacao sustenta a coorte do painel: nao pode sumir")
                .isEqualTo(1);
    }

    /** A clinica continua enxergando o que o tutor escondeu do proprio aplicativo. */
    @Test
    @DisplayName("o pet inativado continua visivel para a clinica")
    void oPetInativadoContinuaNaGestao() throws Exception {
        mockMvc.perform(delete("/api/tutor/pets/" + petDeA)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isNoContent());

        TenantContext.set(clinica);
        assertThat(petService.findByTutor(tutorA, PageRequest.of(0, 20)).getContent())
                .as("a rota de gestao nao muda: /api/pets/tutor/{id} devolve o pet inativado")
                .extracting(PetResponse::id)
                .contains(petDeA);
    }

    @Test
    @DisplayName("a lista de pets do tutor nao traz o pet do outro")
    void aListaDePetsNaoAtravessaTutor() throws Exception {
        mockMvc.perform(get("/api/tutor/pets").header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + petDeA + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.id == " + petDeB + ")]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.tutorId != " + tutorA + ")]").doesNotExist());
    }

    @Test
    @DisplayName("os sub-recursos do pet passam pela mesma verificacao de posse")
    void osSubRecursosTambemConferemAPosse() throws Exception {
        for (String sub : new String[]{"ficha-tecnica", "consultas", "vacinas"}) {
            mockMvc.perform(get("/api/tutor/pets/" + petDeA + "/" + sub)
                            .header(HttpHeaders.AUTHORIZATION, tokenDeA))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/tutor/pets/" + petDeB + "/" + sub)
                            .header(HttpHeaders.AUTHORIZATION, tokenDeA))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------- agendamentos ----------

    @Test
    @DisplayName("o tutor nao agenda para o pet do outro")
    void naoAgendaParaOPetDoOutro() throws Exception {
        mockMvc.perform(post("/api/tutor/agendamentos")
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeAgendamento(petDeB)))
                .andExpect(status().isNotFound());

        assertThat(contar("select count(*) from TB_CLV_AGENDAMENTO where id_pet = %d".formatted(petDeB)))
                .as("nenhuma linha pode ter nascido para o pet do outro tutor")
                .isZero();

        mockMvc.perform(post("/api/tutor/agendamentos")
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeAgendamento(petDeA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.petId").value(petDeA));
    }

    @Test
    @DisplayName("o tutor nao le, nao remarca e nao cancela o compromisso do outro")
    void naoAlcancaOAgendamentoDoOutro() throws Exception {
        Long deB = agendamentoDe(tokenDeB, petDeB);

        mockMvc.perform(get("/api/tutor/agendamentos/" + deB)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/tutor/agendamentos/" + deB)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeAgendamento(petDeA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/tutor/agendamentos/" + deB)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isNotFound());

        assertThat(contar("""
                select count(*) from TB_CLV_AGENDAMENTO
                 where id_agendamento = %d and id_pet = %d and ds_status = 'SOLICITADO'
                """.formatted(deB, petDeB)))
                .as("o compromisso do outro tutor tem que estar intacto")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a lista de agendamentos do tutor nao traz o compromisso do outro")
    void aListaDeAgendamentosNaoAtravessaTutor() throws Exception {
        Long deA = agendamentoDe(tokenDeA, petDeA);
        Long deB = agendamentoDe(tokenDeB, petDeB);

        mockMvc.perform(get("/api/tutor/agendamentos").header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + deA + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.id == " + deB + ")]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.petId != " + petDeA + ")]").doesNotExist());
    }

    /** Cancelar e transicao de status, e nao DELETE: a linha continua la. */
    @Test
    @DisplayName("cancelar o proprio compromisso o leva a CANCELADO sem apagar a linha")
    void cancelarPreservaORegistro() throws Exception {
        Long deA = agendamentoDe(tokenDeA, petDeA);

        mockMvc.perform(delete("/api/tutor/agendamentos/" + deA)
                        .header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isNoContent());

        assertThat(contar("""
                select count(*) from TB_CLV_AGENDAMENTO
                 where id_agendamento = %d and ds_status = 'CANCELADO'
                """.formatted(deA)))
                .as("o cancelamento nao pode apagar o registro")
                .isEqualTo(1);
    }

    // ---------- perfil ----------

    @Test
    @DisplayName("/me devolve o tutor do token, e nao aceita outro")
    void meSaiDoToken() throws Exception {
        mockMvc.perform(get("/api/tutor/me").header(HttpHeaders.AUTHORIZATION, tokenDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tutorA))
                .andExpect(jsonPath("$.clinicaId").value(clinica));

        mockMvc.perform(get("/api/tutor/me").header(HttpHeaders.AUTHORIZATION, tokenDeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tutorB));
    }

    // ---------- utilitarios ----------

    /** Marca um compromisso pela propria API, e devolve o id que ela criou. */
    private Long agendamentoDe(String token, Long pet) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/tutor/agendamentos")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeAgendamento(pet)))
                .andExpect(status().isCreated())
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        return Long.valueOf(corpo.replaceFirst("^\\{\"id\":(\\d+).*$", "$1"));
    }

    private String corpoDePet(String nome) {
        return """
                {"nome":"%s","sexo":"M","porte":"MEDIO","status":"ATIVO","racaId":%d}
                """.formatted(nome, raca);
    }

    private String corpoDeAgendamento(Long pet) {
        return """
                {"dtAgendamento":"%s","hrAgendamento":"14:00","status":"SOLICITADO",
                 "canalOrigem":"APP","petId":%d,"veterinarioId":%d}
                """.formatted(LocalDate.now().plusDays(7), pet, veterinario);
    }

    private String bearer(Long idTutor, String sufixo) {
        return "Bearer " + jwtService.generateAccessToken(idTutor,
                "tutor-" + sufixo + "-" + idTutor + "@teste.local", "Tutor " + sufixo.toUpperCase(),
                TipoUsuario.TUTOR, clinica);
    }

    private int contar(String sql) {
        // nativa de proposito: a conferencia nao pode depender do filtro de
        // tenant nem do mapeamento que o teste esta exercitando
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).intValue();
    }
}
