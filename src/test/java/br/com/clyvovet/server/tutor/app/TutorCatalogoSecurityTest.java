package br.com.clyvovet.server.tutor.app;

import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.clinica.ClinicaController;
import br.com.clyvovet.server.clinica.ClinicaService;
import br.com.clyvovet.server.config.SecurityConfig;
import br.com.clyvovet.server.config.WebMvcConfig;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.especie.EspecieController;
import br.com.clyvovet.server.especie.EspecieService;
import br.com.clyvovet.server.raca.RacaController;
import br.com.clyvovet.server.raca.RacaService;
import br.com.clyvovet.server.veterinario.VeterinarioController;
import br.com.clyvovet.server.veterinario.VeterinarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O catalogo do tutor abriu leitura, e nada mais que leitura.
 *
 * <p>O aplicativo precisava de especie, raca e veterinario para preencher os
 * formularios de pet e de agendamento — {@code racaId} e {@code veterinarioId}
 * sao obrigatorios no corpo. O risco de abrir isso e abrir junto o CRUD do
 * catalogo, que muda o cuidado de todos os pets da clinica; e o risco de nao
 * abrir e o aplicativo embutir uma copia fixa das opcoes, que envelhece calada
 * assim que a clinica cadastra a proxima raca.
 *
 * <p>Estes testes prendem os dois lados: o tutor le
 * {@code /api/tutor/catalogo/**} e continua barrado em {@code /api/especies},
 * {@code /api/racas} e {@code /api/veterinarios}.
 *
 * <p>O ultimo teste cobre a outra rota que este trabalho abriu:
 * {@code /api/clinicas/publicas}, unica leitura anonima da API, sem que
 * {@code /api/clinicas} deixe de pedir token.
 */
@WebMvcTest(controllers = {
        TutorCatalogoController.class,
        EspecieController.class,
        RacaController.class,
        VeterinarioController.class,
        ClinicaController.class
})
@Import({SecurityConfig.class, WebMvcConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-longo-o-bastante-para-hmac-sha-384-clyvovet",
        "app.jwt.access-token-expiration-ms=900000"
})
class TutorCatalogoSecurityTest {

    private static final long ID_CLINICA = 47L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PosseDoTutor posse;

    @MockitoBean
    private EspecieService especieService;

    @MockitoBean
    private RacaService racaService;

    @MockitoBean
    private VeterinarioService veterinarioService;

    @MockitoBean
    private ClinicaService clinicaService;

    @BeforeEach
    void devolverPaginasVazias() {
        when(posse.autenticado()).thenReturn(new AuthenticatedUser(
                1L, "tutor@clyvo.com", "Tutor de teste", TipoUsuario.TUTOR, ID_CLINICA));
        when(especieService.findAll(any(Pageable.class))).thenReturn(paginaVazia());
        when(racaService.findAll(any(Pageable.class))).thenReturn(paginaVazia());
        when(racaService.findByEspecie(anyLong(), any(Pageable.class))).thenReturn(paginaVazia());
        when(veterinarioService.findByClinica(anyLong(), any(Pageable.class))).thenReturn(paginaVazia());
        when(clinicaService.findAllPublicas(any(Pageable.class))).thenReturn(paginaVazia());
    }

    private static <T> Page<T> paginaVazia() {
        return new PageImpl<>(List.of());
    }

    @Test
    @DisplayName("o tutor le o catalogo do aplicativo")
    void tutorLeOCatalogo() throws Exception {
        for (String rota : new String[]{"especies", "racas", "racas?especieId=3", "veterinarios"}) {
            mockMvc.perform(get("/api/tutor/catalogo/" + rota)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                    .andExpect(status().isOk());
        }
    }

    /**
     * A clinica do veterinario sai do token. Se um dia virar parametro, um teste
     * que so olhasse o 200 continuaria passando e o buraco voltaria calado — por
     * isso a assercao e sobre o valor que chegou ao service.
     */
    @Test
    @DisplayName("a lista de veterinarios e a da clinica do token")
    void veterinariosSaemDaClinicaDoToken() throws Exception {
        mockMvc.perform(get("/api/tutor/catalogo/veterinarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isOk());

        verify(veterinarioService).findByClinica(eq(ID_CLINICA), any(Pageable.class));
    }

    @Test
    @DisplayName("ler o catalogo do aplicativo nao e poder edita-lo")
    void tutorContinuaBarradoNoCrudDoCatalogo() throws Exception {
        for (String rota : new String[]{"/api/especies", "/api/racas", "/api/veterinarios"}) {
            mockMvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    @Test
    @DisplayName("a equipe da clinica nao entra no catalogo do aplicativo")
    void equipeNaoEntraNoCatalogoDoTutor() throws Exception {
        for (TipoUsuario tipo : new TipoUsuario[]{TipoUsuario.COLABORADOR, TipoUsuario.VETERINARIO}) {
            mockMvc.perform(get("/api/tutor/catalogo/racas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(posse, racaService);
    }

    @Test
    @DisplayName("sem token o catalogo do aplicativo devolve 401")
    void anonimoNaoLeOCatalogo() throws Exception {
        mockMvc.perform(get("/api/tutor/catalogo/racas"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(posse, racaService);
    }

    @Test
    @DisplayName("a vitrine de clinicas e publica; o cadastro de clinicas nao")
    void clinicasPublicasAbrem() throws Exception {
        mockMvc.perform(get("/api/clinicas/publicas"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/clinicas"))
                .andExpect(status().isUnauthorized());

        // e nem com token de tutor: a lista completa continua sendo da gestao
        mockMvc.perform(get("/api/clinicas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR)))
                .andExpect(status().isForbidden());
    }

    private String bearer(TipoUsuario tipo) {
        return "Bearer " + jwtService.generateAccessToken(
                1L, tipo.name().toLowerCase() + "@clinica.com", "Usuario de teste", tipo, ID_CLINICA);
    }
}
