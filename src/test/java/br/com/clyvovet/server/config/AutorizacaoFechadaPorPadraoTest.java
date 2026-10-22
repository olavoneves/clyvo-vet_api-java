package br.com.clyvovet.server.config;

import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.clinica.ClinicaController;
import br.com.clyvovet.server.clinica.ClinicaResponse;
import br.com.clyvovet.server.clinica.ClinicaService;
import br.com.clyvovet.server.enums.CanalPreferencial;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.especie.EspecieController;
import br.com.clyvovet.server.especie.EspecieService;
import br.com.clyvovet.server.iot.alerta.AlertaIotController;
import br.com.clyvovet.server.iot.alerta.AlertaIotService;
import br.com.clyvovet.server.tutor.TutorController;
import br.com.clyvovet.server.tutor.TutorResponse;
import br.com.clyvovet.server.tutor.TutorService;
import br.com.clyvovet.server.veterinario.VeterinarioController;
import br.com.clyvovet.server.veterinario.VeterinarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A postura da cadeia da API: fechada por padrao.
 *
 * <p>A regra final era {@code anyRequest().authenticated()}, e rota que ninguem
 * declarasse nascia alcancavel por qualquer perfil autenticado. Foi assim que um
 * token de tutor chegou a {@code GET /api/tutores} — o cadastro, com e-mail e
 * telefone, de todos os tutores da clinica — e aos catalogos e as rotas de IoT.
 * Hoje a regra final e {@code denyAll}.
 *
 * <p>O teste que mais importa aqui e o da rota nao declarada. Os outros afirmam
 * que uma lista especifica esta certa hoje; aquele afirma que a proxima rota,
 * que ainda nao existe, nasce fechada — e e a unica forma de testar uma omissao
 * futura.
 *
 * <p>Fatia web apenas, sem banco: o que esta sob teste e a
 * {@link SecurityConfig}, e os tokens saem do {@code JwtService} de verdade.
 */
@WebMvcTest(controllers = {
        TutorController.class,
        ClinicaController.class,
        EspecieController.class,
        AlertaIotController.class,
        VeterinarioController.class
})
@Import({SecurityConfig.class, WebMvcConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-longo-o-bastante-para-hmac-sha-384-clyvovet",
        "app.jwt.access-token-expiration-ms=900000"
})
class AutorizacaoFechadaPorPadraoTest {

    private static final String CADASTRO_DE_TUTOR = """
            {"nome":"Joana Ribeiro","email":"joana@exemplo.com","senha":"segredo123",
             "canalPreferencial":"APP","clinicaId":47}
            """;

    private static final String CADASTRO_DE_CLINICA = """
            {"nome":"Clinica Vida Animal","cnpj":"12345678000199",
             "logradouro":"Rua das Flores, 100","cidade":"Sao Paulo","estado":"SP"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TutorService tutorService;

    @MockitoBean
    private ClinicaService clinicaService;

    @MockitoBean
    private EspecieService especieService;

    @MockitoBean
    private AlertaIotService alertaIotService;

    @MockitoBean
    private VeterinarioService veterinarioService;

    /**
     * O caso que motivou a rodada: o cadastro de todos os tutores da clinica,
     * alcancavel por qualquer um deles.
     */
    @Test
    @DisplayName("token de tutor nao alcanca o cadastro de tutores da clinica")
    void tutorNaoLeOCadastroDeTutores() throws Exception {
        mockMvc.perform(get("/api/tutores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/tutores/9")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tutorService);
    }

    @Test
    @DisplayName("token de tutor nao alcanca o catalogo clinico nem a telemetria")
    void tutorNaoAlcancaCatalogoNemIot() throws Exception {
        mockMvc.perform(get("/api/especies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/alertas-iot")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/veterinarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(especieService, alertaIotService, veterinarioService);
    }

    /**
     * Os dois cadastros que acontecem sem token: a clinica se cadastra na
     * plataforma e o tutor se cadastra na clinica. O {@code denyAll} nao pode
     * ter fechado nenhum dos dois.
     */
    @Test
    @DisplayName("os dois cadastros publicos continuam abertos sem token")
    void osCadastrosPublicosContinuamAbertos() throws Exception {
        given(tutorService.create(any())).willReturn(new TutorResponse(
                7L, "Joana Ribeiro", "joana@exemplo.com", null, null,
                CanalPreferencial.APP, LocalDate.now(), 47L));
        given(clinicaService.create(any())).willReturn(new ClinicaResponse(
                47L, "Clinica Vida Animal", "12345678000199", "Rua das Flores, 100",
                null, null, "Sao Paulo", "SP", null, null, BigDecimal.ZERO));

        mockMvc.perform(post("/api/tutores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CADASTRO_DE_TUTOR))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clinicas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CADASTRO_DE_CLINICA))
                .andExpect(status().isCreated());
    }

    /** O GET de tutores nao virou publico junto com o POST. */
    @Test
    @DisplayName("so o POST dos cadastros e publico: o GET exige token")
    void oGetDosCadastrosNaoEPublico() throws Exception {
        mockMvc.perform(get("/api/tutores"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/clinicas"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tutorService, clinicaService);
    }

    @Test
    @DisplayName("a equipe da clinica continua alcancando as rotas de clinica")
    void aEquipeContinuaAlcancandoAsRotasDeClinica() throws Exception {
        for (TipoUsuario tipo : new TipoUsuario[]{TipoUsuario.COLABORADOR, TipoUsuario.VETERINARIO}) {
            mockMvc.perform(get("/api/tutores").header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/especies").header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/alertas-iot").header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/clinicas").header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andExpect(status().isOk());
        }
    }

    /** A excecao que ja existia continua valendo: contratar veterinario e so do colaborador. */
    @Test
    @DisplayName("POST /api/veterinarios continua restrito ao colaborador")
    void contratarVeterinarioContinuaSendoDoColaborador() throws Exception {
        mockMvc.perform(post("/api/veterinarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.VETERINARIO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Ana Prado","crmv":"SP-12345","especialidade":"Clinica geral",
                                 "email":"ana.prado@clinica.com","senha":"segredo123"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(veterinarioService);
    }

    /**
     * A afirmacao central: rota que ninguem declarou e negada.
     *
     * <p>Com {@code anyRequest().authenticated()} este caminho responderia 404 —
     * autorizado, e so entao sem handler. Com {@code denyAll} ele para no 403,
     * antes de existir controller. E o teste que protege a proxima rota, a que
     * ainda nao foi escrita.
     */
    @Test
    @DisplayName("rota nao declarada e negada, mesmo para a equipe da clinica")
    void rotaNaoDeclaradaENegada() throws Exception {
        for (TipoUsuario tipo : TipoUsuario.values()) {
            mockMvc.perform(get("/api/rota-que-ninguem-declarou")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    private String bearer(TipoUsuario tipo) {
        return "Bearer " + jwtService.generateAccessToken(
                1L, tipo.name().toLowerCase() + "@clinica.com", "Usuario de teste", tipo, 47L);
    }
}
