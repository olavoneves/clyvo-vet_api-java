package br.com.clyvovet.server.agente;

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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A porta do agente: quem entra, e o que acontece quando ele nao existe.
 *
 * <p>Fatia web apenas, sem banco. O que esta sob teste e a cadeia de filtros e o
 * tradutor de excecao, entao os tokens saem do {@code JwtService} de verdade em
 * vez de um SecurityContext montado a mao — e o mesmo caminho que o aplicativo
 * do tutor percorre.
 */
@WebMvcTest(controllers = AgenteController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-longo-o-bastante-para-hmac-sha-384-clyvovet",
        "app.jwt.access-token-expiration-ms=900000"
})
class AgenteControllerSecurityTest {

    private static final String CORPO = """
            {"idPet":9,"texto":"quero marcar a vacina"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AgenteService agenteService;

    @Test
    void semTokenDevolve401() throws Exception {
        mockMvc.perform(post("/api/agente/mensagens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agenteService);
    }

    /** As telas de gestao ja marcam consulta; o agente e a superficie do tutor. */
    @Test
    void veterinarioNaoUsaOAgente() throws Exception {
        mockMvc.perform(post("/api/agente/mensagens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.VETERINARIO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agenteService);
    }

    @Test
    void colaboradorNaoUsaOAgente() throws Exception {
        mockMvc.perform(get("/api/agente/conversas/9")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.COLABORADOR)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agenteService);
    }

    @Test
    void tutorConversa() throws Exception {
        given(agenteService.responder(anyLong(), any()))
                .willReturn(new RespostaDoAgenteResponse("Tenho quinta às 14:00.", List.of(), false));

        mockMvc.perform(post("/api/agente/mensagens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Tenho quinta às 14:00."))
                .andExpect(jsonPath("$.escalado").value(false));
    }

    @Test
    void mensagemVaziaNaoChegaAoAgente() throws Exception {
        mockMvc.perform(post("/api/agente/mensagens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idPet\":9,\"texto\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(agenteService);
    }

    /**
     * Sem ANTHROPIC_API_KEY: 503, e nada mais.
     *
     * <p>O que este teste protege e a promessa de que o agente e opcional. Se
     * alguem trocar o handler dedicado por um catch generico, isto vira 500 — e
     * 500 num deploy sem chave e um incidente onde deveria haver uma
     * funcionalidade desligada.
     */
    @Test
    void semChaveDeApiDevolve503() throws Exception {
        willThrow(new AgenteDesligadoException()).given(agenteService).responder(anyLong(), any());

        mockMvc.perform(post("/api/agente/mensagens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TipoUsuario.TUTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"));
    }

    private String bearer(TipoUsuario tipo) {
        return "Bearer " + jwtService.generateAccessToken(
                1L, tipo.name().toLowerCase() + "@clinica.com", "Usuario de teste", tipo, 47L);
    }
}
