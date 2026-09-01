package br.com.clyvovet.server.veterinario;

import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.config.SecurityConfig;
import br.com.clyvovet.server.config.WebMvcConfig;
import br.com.clyvovet.server.enums.TipoUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /veterinarios deixou de ser rota publica: cadastrar veterinario e ato
 * administrativo da clinica, feito por colaborador autenticado.
 *
 * <p>Fatia web apenas — sem banco. O que esta sob teste e a cadeia de filtros
 * do Spring Security, entao os tokens sao emitidos pelo JwtService de verdade
 * em vez de um SecurityContext montado a mao.
 *
 * <p>A rota e /api/veterinarios: o WebMvcConfig prefixa todo @RestController,
 * e sem importa-lo a fatia responderia em /veterinarios e o teste passaria
 * verificando uma rota que nao existe em producao.
 */
@WebMvcTest(controllers = VeterinarioController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-longo-o-bastante-para-hmac-sha-384-clyvovet",
        "app.jwt.access-token-expiration-ms=900000"
})
class VeterinarioControllerSecurityTest {

    private static final String CORPO_VALIDO = """
            {"nome":"Ana Prado","crmv":"SP-12345","especialidade":"Clinica geral",
             "email":"ana.prado@clinica.com","senha":"segredo123"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private VeterinarioService veterinarioService;

    @Test
    void postSemAutenticacaoDevolve401() throws Exception {
        mockMvc.perform(post("/api/veterinarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        // 401 antes do controller: a requisicao anonima nao chega no service
        verifyNoInteractions(veterinarioService);
    }

    @Test
    void postComTokenDeTutorDevolve403() throws Exception {
        mockMvc.perform(post("/api/veterinarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(veterinarioService);
    }

    @Test
    void postComTokenDeVeterinarioDevolve403() throws Exception {
        mockMvc.perform(post("/api/veterinarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.VETERINARIO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(veterinarioService);
    }

    @Test
    void postComTokenDeColaboradorCria() throws Exception {
        given(veterinarioService.create(any(VeterinarioRequest.class)))
                .willReturn(new VeterinarioResponse(7L, "Ana Prado", "SP-12345",
                        "Clinica geral", 47L, "Clinica Vida Animal"));

        mockMvc.perform(post("/api/veterinarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.COLABORADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.clinicaId").value(47));
    }

    private String bearer(TipoUsuario tipo) {
        return "Bearer " + jwtService.generateAccessToken(
                1L, tipo.name().toLowerCase() + "@clinica.com", "Usuario de teste", tipo, 47L);
    }
}
