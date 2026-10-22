package br.com.clyvovet.server.tutor.app;

import br.com.clyvovet.server.agendamento.AgendamentoController;
import br.com.clyvovet.server.agendamento.AgendamentoService;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.config.SecurityConfig;
import br.com.clyvovet.server.config.WebMvcConfig;
import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.fichaclinica.alergia.AlergiaPetService;
import br.com.clyvovet.server.fichaclinica.condicao.CondicaoPetService;
import br.com.clyvovet.server.iot.alerta.AlertaIotService;
import br.com.clyvovet.server.pet.PetController;
import br.com.clyvovet.server.pet.PetService;
import br.com.clyvovet.server.tutor.TutorService;
import br.com.clyvovet.server.vacinacao.AplicacaoVacinaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fronteira entre as duas superficies, no nivel da cadeia de filtros.
 *
 * <p>Ate esta rodada {@code /api/pets/**} e {@code /api/agendamentos/**} caiam no
 * {@code anyRequest().authenticated()}, e um token de tutor as alcancava — sem
 * nenhuma verificacao de dono do outro lado. O que os dois primeiros testes
 * afirmam e que isso acabou; o terceiro afirma o inverso, que a clinica nao entra
 * na superficie do aplicativo.
 *
 * <p>Fatia web apenas, sem banco: o que esta sob teste e a
 * {@link SecurityConfig}, entao os tokens saem do {@code JwtService} de verdade
 * e o 403 tem que acontecer antes de o controller existir — e por isso todo
 * teste termina em {@code verifyNoInteractions}.
 */
@WebMvcTest(controllers = {
        PetController.class,
        AgendamentoController.class,
        TutorPerfilController.class,
        TutorPetController.class,
        TutorAgendamentoController.class
})
@Import({SecurityConfig.class, WebMvcConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-longo-o-bastante-para-hmac-sha-384-clyvovet",
        "app.jwt.access-token-expiration-ms=900000"
})
class TutorApiRotasSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PosseDoTutor posse;

    @MockitoBean
    private PetService petService;

    @MockitoBean
    private AgendamentoService agendamentoService;

    @MockitoBean
    private TutorService tutorService;

    @MockitoBean
    private ConsultaService consultaService;

    @MockitoBean
    private AplicacaoVacinaService vacinaService;

    @MockitoBean
    private AlertaIotService alertaIotService;

    @MockitoBean
    private CondicaoPetService condicaoPetService;

    @MockitoBean
    private AlergiaPetService alergiaPetService;

    @Test
    @DisplayName("token de tutor nao alcanca a API de gestao de pets")
    void tutorNaoAlcancaPetsDaGestao() throws Exception {
        mockMvc.perform(get("/api/pets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // era esta a rota que aceitava o id do dono pela URL
        mockMvc.perform(get("/api/pets/tutor/9")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/pets/9/ficha-tecnica")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(petService);
    }

    @Test
    @DisplayName("token de tutor nao alcanca a API de gestao de agendamentos")
    void tutorNaoAlcancaAgendamentosDaGestao() throws Exception {
        mockMvc.perform(get("/api/agendamentos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/agendamentos/9")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agendamentoService);
    }

    @Test
    @DisplayName("a equipe da clinica nao entra na superficie do aplicativo do tutor")
    void colaboradorEVeterinarioNaoAlcancamASuperficieDoTutor() throws Exception {
        for (TipoUsuario tipo : new TipoUsuario[]{TipoUsuario.COLABORADOR, TipoUsuario.VETERINARIO}) {
            mockMvc.perform(get("/api/tutor/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));

            mockMvc.perform(get("/api/tutor/pets")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/tutor/agendamentos")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(posse, tutorService, petService, agendamentoService);
    }

    @Test
    @DisplayName("sem token a superficie do tutor devolve 401, e nao 403")
    void anonimoLeva401() throws Exception {
        mockMvc.perform(get("/api/tutor/pets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(posse, petService);
    }

    /** A gestao continua alcancando a gestao: o 403 acima e de perfil, nao de rota quebrada. */
    @Test
    @DisplayName("colaborador continua alcancando a API de gestao")
    void colaboradorAlcancaAGestao() throws Exception {
        mockMvc.perform(get("/api/pets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.COLABORADOR)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/agendamentos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.VETERINARIO)))
                .andExpect(status().isOk());
    }

    private String bearer(TipoUsuario tipo) {
        return "Bearer " + jwtService.generateAccessToken(
                1L, tipo.name().toLowerCase() + "@clinica.com", "Usuario de teste", tipo, 47L);
    }
}
