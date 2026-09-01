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
import br.com.clyvovet.server.enums.TipoUsuario;
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
        AgendaWebController.class
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Acesso da equipe")));
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
                        // o lift precisa chegar formatado na tela, nao so no JSON do grafico
                        org.hamcrest.Matchers.containsString("p.p."))));
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

    /** Tutor autentica na API, mas nao nas telas de gestao da clinica. */
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
                new PainelReceitaResponse.Grupo(96, 30, new BigDecimal("31.3"),
                        new BigDecimal("16800.00"), new BigDecimal("4900.00")),
                new PainelReceitaResponse.Grupo(24, 3, new BigDecimal("12.5"),
                        new BigDecimal("1650.00"), new BigDecimal("1300.00")),
                new BigDecimal("18.8"));
    }

    private static PainelReceitaResponse painelVazio() {
        return new PainelReceitaResponse(
                LocalDate.of(2026, 9, 1),
                new PainelReceitaResponse.Funil(0, 0, 0, 0, 0, 0),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new PainelReceitaResponse.Grupo(0, 0, null, BigDecimal.ZERO, BigDecimal.ZERO),
                new PainelReceitaResponse.Grupo(0, 0, null, BigDecimal.ZERO, BigDecimal.ZERO),
                null);
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
                "VAC-V10", "Vacinação V10", ObrigacaoStatus.PREVISTA,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 25),
                false, new BigDecimal("180.00"), null);
    }
}
