package br.com.clyvovet.server.web;

import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.auth.ClinicaUserDetails;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.config.SecurityConfig;
import br.com.clyvovet.server.config.WebMvcConfig;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.enums.PetPorte;
import br.com.clyvovet.server.enums.PetSexo;
import br.com.clyvovet.server.enums.PetStatus;
import br.com.clyvovet.server.enums.ProtocoloCategoria;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.obrigacao.ObrigacaoResponse;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.obrigacao.web.AgendaWebController;
import br.com.clyvovet.server.painel.PainelReceitaResponse;
import br.com.clyvovet.server.painel.PainelReceitaService;
import br.com.clyvovet.server.painel.web.PainelWebController;
import br.com.clyvovet.server.pet.PetFichaTecnicaResponse;
import br.com.clyvovet.server.pet.PetResponse;
import br.com.clyvovet.server.pet.PetService;
import br.com.clyvovet.server.pet.web.PetWebController;
import br.com.clyvovet.server.auth.web.LoginWebController;
import br.com.clyvovet.server.tutor.web.TutorWebController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renderiza cada tela de ponta a ponta com dados de mentira.
 *
 * <p>Erro de Thymeleaf so aparece na renderizacao — expressao invalida, assinatura
 * de fragmento trocada, objeto de expressao que nao existe mais nesta versao. Nada
 * disso quebra a compilacao, entao sem um teste que de fato renderize a primeira
 * pessoa a descobrir seria quem abrisse a tela na apresentacao.
 *
 * <p>Nao valida conteudo de negocio: o que esta sob teste e o template, e os
 * numeros vem dos services, que tem os proprios testes.
 */
@WebMvcTest(controllers = {
        LoginWebController.class,
        PainelWebController.class,
        PetWebController.class,
        AgendaWebController.class,
        TutorWebController.class
})
@Import({SecurityConfig.class, WebMvcConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-longo-o-bastante-para-hmac-sha-384-clyvovet",
        "app.jwt.access-token-expiration-ms=900000"
})
class PaginasRenderizamTest {

    private static final ClinicaUserDetails COLABORADOR = new ClinicaUserDetails(
            new AuthenticatedUser(1L, "master@clyvovet.com", "Master da Clinica",
                    TipoUsuario.COLABORADOR, 47L),
            "irrelevante-nao-ha-autenticacao-por-senha-aqui");

    private static final ClinicaUserDetails TUTOR = new ClinicaUserDetails(
            new AuthenticatedUser(3L, "joana@exemplo.com", "Joana Ribeiro",
                    TipoUsuario.TUTOR, 47L),
            "irrelevante-nao-ha-autenticacao-por-senha-aqui");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PainelReceitaService painelService;

    @MockitoBean
    private PetService petService;

    @MockitoBean
    private ObrigacaoService obrigacaoService;

    @Test
    void loginRenderizaSemAutenticacao() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Entre com seu e-mail")));
    }

    @Test
    void paginaProtegidaSemSessaoVaiParaOLogin() throws Exception {
        mockMvc.perform(get("/painel/receita"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void painelRenderizaComFunilNumeroEComparacao() throws Exception {
        given(painelService.doMes(any(LocalDate.class))).willReturn(painelDeExemplo());

        mockMvc.perform(get("/painel/receita").with(user(COLABORADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Receita recuperada este mês"),
                        org.hamcrest.Matchers.containsString("graficoFunil"),
                        org.hamcrest.Matchers.containsString("graficoComparacao"),
                        // o delta precisa chegar formatado na tela, nao so no JSON do grafico
                        org.hamcrest.Matchers.containsString("p.p."),
                        // base amostral visivel: delta sem denominador ao lado nao se defende
                        org.hamcrest.Matchers.containsString("833 de 1411"),
                        org.hamcrest.Matchers.containsString("66 de 202"),
                        // e a origem do numero declarada na propria tela
                        org.hamcrest.Matchers.containsString("sorteio determinístico por pet"))));
    }

    /** Painel de mes sem movimento: as divisoes do funil nao podem estourar. */
    @Test
    void painelRenderizaMesZerado() throws Exception {
        given(painelService.doMes(any(LocalDate.class))).willReturn(painelVazio());

        mockMvc.perform(get("/painel/receita").with(user(COLABORADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sem dados")));
    }

    @Test
    void listaDePetsRenderiza() throws Exception {
        given(petService.findAll(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(petDeExemplo()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/pets").with(user(COLABORADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rex")));
    }

    @Test
    void listaDePetsVaziaRenderiza() throws Exception {
        given(petService.findAll(any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/pets").with(user(COLABORADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nenhum pet cadastrado")));
    }

    @Test
    void fichaDoPetRenderizaComObrigacoes() throws Exception {
        given(petService.getFichaTecnica(9L)).willReturn(fichaDeExemplo());
        given(obrigacaoService.buscar(isNull(), isNull(), isNull(), eq(9L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(obrigacaoDeExemplo())));

        mockMvc.perform(get("/pets/9").with(user(COLABORADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Rex"),
                        org.hamcrest.Matchers.containsString("Reforço anual"))));
    }

    @Test
    void agendaRenderizaOsDoisBlocos() throws Exception {
        given(obrigacaoService.buscar(isNull(), any(LocalDate.class), any(LocalDate.class),
                isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(obrigacaoDeExemplo())));

        mockMvc.perform(get("/agenda").with(user(COLABORADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Hoje"),
                        org.hamcrest.Matchers.containsString("Próximos dias"))));
    }

    /**
     * Pet inexistente — ou de outra clinica, que da no mesmo para quem pergunta —
     * tem que virar 404, nao 500.
     *
     * <p>Regressao real: ao restringir o GlobalExceptionHandler aos
     * @RestController, as paginas ficaram sem tradutor de excecao e passaram a
     * devolver 500. Quem responde por elas agora e o @ResponseStatus na propria
     * excecao de dominio.
     */
    @Test
    void petInexistenteNaTelaVira404() throws Exception {
        given(petService.getFichaTecnica(9999L))
                .willThrow(new EntityNotFoundException("Pet", 9999L));

        mockMvc.perform(get("/pets/9999").with(user(COLABORADOR)))
                .andExpect(status().isNotFound());
    }

    // ---------- superficie do tutor ----------

    /**
     * A tela que fecha o laco da demonstracao.
     *
     * <p>Renderiza pendencia, carteirinha e o campo de conversa numa passada so,
     * porque e assim que ela aparece para o tutor: se a carteirinha quebrar, a
     * pagina inteira quebra, e nao adianta o resto ter passado.
     */
    @Test
    void telaDoTutorRenderiza() throws Exception {
        given(petService.findByTutor(eq(3L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(petDeExemplo())));
        given(petService.getFichaTecnica(9L)).willReturn(fichaDeExemplo());
        given(obrigacaoService.doPetComStatus(eq(9L), any(), any(Pageable.class)))
                .willReturn(List.of(obrigacaoDeExemplo()));

        mockMvc.perform(get("/tutor/pets/9").with(user(TUTOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Rex"),
                        org.hamcrest.Matchers.containsString("Cuidados pendentes"),
                        org.hamcrest.Matchers.containsString("Reforço anual"),
                        org.hamcrest.Matchers.containsString("Carteirinha de vacinas"),
                        // o campo de conversa e o token que o faz funcionar
                        org.hamcrest.Matchers.containsString("Falar com a clínica"),
                        org.hamcrest.Matchers.containsString("/api/agente/mensagens"))));
    }

    /** /tutor sem id escolhe o primeiro pet: e para onde o login manda o tutor. */
    @Test
    void tutorSemIdCaiNoPrimeiroPet() throws Exception {
        given(petService.findByTutor(eq(3L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(petDeExemplo())));
        given(petService.getFichaTecnica(9L)).willReturn(fichaDeExemplo());
        given(obrigacaoService.doPetComStatus(eq(9L), any(), any(Pageable.class)))
                .willReturn(List.of());

        mockMvc.perform(get("/tutor").with(user(TUTOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tudo em dia")));
    }

    @Test
    void tutorSemPetVeExplicacaoEmVezDeErro() throws Exception {
        given(petService.findByTutor(eq(3L), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 50)));

        mockMvc.perform(get("/tutor").with(user(TUTOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Nenhum pet cadastrado")));
    }

    /** Pet de outro tutor nao aparece na lista dele, entao nao existe para a tela. */
    @Test
    void tutorNaoAbrePetDeOutroDono() throws Exception {
        given(petService.findByTutor(eq(3L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(petDeExemplo())));

        mockMvc.perform(get("/tutor/pets/4242").with(user(TUTOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    void colaboradorNaoAlcancaATelaDoTutor() throws Exception {
        mockMvc.perform(get("/tutor").with(user(COLABORADOR)))
                .andExpect(status().isForbidden());
    }

    /** Tutor autentica nas telas, mas nao nas de gestao da clinica. */
    @Test
    void tutorNaoAlcancaAsTelasDaClinica() throws Exception {
        ClinicaUserDetails tutor = new ClinicaUserDetails(
                new AuthenticatedUser(2L, "tutor@exemplo.com", "Tutor", TipoUsuario.TUTOR, 47L),
                "irrelevante");

        mockMvc.perform(get("/painel/receita").with(user(tutor)))
                .andExpect(status().isForbidden());
    }

    private static PainelReceitaResponse painelDeExemplo() {
        return new PainelReceitaResponse(
                LocalDate.of(2026, 9, 1),
                new PainelReceitaResponse.Funil(120, 96, 54, 41, 33, 12),
                new BigDecimal("18450.00"),
                new BigDecimal("6200.00"),
                // comparacao sobre a janela inteira: bases bem maiores que as do mes
                new PainelReceitaResponse.Comparacao(
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 8, 1),
                        new PainelReceitaResponse.Grupo(1411, 833, new BigDecimal("59.0"),
                                new BigDecimal("116800.00"), new BigDecimal("34900.00")),
                        new PainelReceitaResponse.Grupo(202, 66, new BigDecimal("32.7"),
                                new BigDecimal("11650.00"), new BigDecimal("9300.00")),
                        new BigDecimal("26.3")));
    }

    private static PainelReceitaResponse painelVazio() {
        return new PainelReceitaResponse(
                LocalDate.of(2026, 9, 1),
                new PainelReceitaResponse.Funil(0, 0, 0, 0, 0, 0),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new PainelReceitaResponse.Comparacao(null, null,
                        new PainelReceitaResponse.Grupo(0, 0, null, BigDecimal.ZERO, BigDecimal.ZERO),
                        new PainelReceitaResponse.Grupo(0, 0, null, BigDecimal.ZERO, BigDecimal.ZERO),
                        null));
    }

    private static PetResponse petDeExemplo() {
        return new PetResponse(9L, "Rex", LocalDate.of(2020, 3, 14), PetSexo.M,
                "982000123456789", "RGA-77", "Curta", PetPorte.MEDIO, true,
                PetStatus.ATIVO, null, 3L, "Joana Ribeiro", 5L, "Labrador", 47L);
    }

    private static PetFichaTecnicaResponse fichaDeExemplo() {
        return new PetFichaTecnicaResponse(9L, "Rex", LocalDate.of(2020, 3, 14), PetSexo.M,
                "982000123456789", "RGA-77", "Curta", PetPorte.MEDIO, true,
                PetStatus.ATIVO, "Pet dócil.", 3L, "Joana Ribeiro",
                "joana@exemplo.com", "11999990000", 5L, "Labrador", 1L, "Canina");
    }

    private static ObrigacaoResponse obrigacaoDeExemplo() {
        return new ObrigacaoResponse(101L, 9L, "Rex", 4L, "Reforço anual",
                "VAC-V10", "Vacinação V10", ProtocoloCategoria.VACINA, ObrigacaoStatus.PREVISTA,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 25),
                false, new BigDecimal("180.00"), null);
    }
}
