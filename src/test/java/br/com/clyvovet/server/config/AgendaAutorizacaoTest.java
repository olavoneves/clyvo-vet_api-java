package br.com.clyvovet.server.config;

import br.com.clyvovet.server.agendamento.AgendaService;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.auth.ClinicaUserDetails;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.obrigacao.web.AgendaWebController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem alcanca a agenda navegavel.
 *
 * <p>Os parametros novos — {@code inicio} e {@code minha} — nao abrem nada: a
 * rota continua sob {@code /agenda/**}, que {@code PAGINAS_DA_CLINICA} reserva
 * para a equipe. O que se trava aqui e que nenhuma combinacao deles leva o
 * tutor, ou quem nao tem sessao, ate o service.
 */
@WebMvcTest(controllers = AgendaWebController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-longo-o-bastante-para-hmac-sha-384-clyvovet",
        "app.jwt.access-token-expiration-ms=900000"
})
class AgendaAutorizacaoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgendaService agendaService;

    @Test
    @DisplayName("veterinario e colaborador navegam pelas semanas")
    void aEquipeNavega() throws Exception {
        for (TipoUsuario tipo : new TipoUsuario[]{TipoUsuario.VETERINARIO, TipoUsuario.COLABORADOR}) {
            mockMvc.perform(get("/agenda").param("inicio", "2026-11-02").param("minha", "false")
                            .with(user(sessao(tipo))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("tutor nao ve a agenda da clinica, com ou sem parametro")
    void tutorNaoVeAAgenda() throws Exception {
        mockMvc.perform(get("/agenda").with(user(sessao(TipoUsuario.TUTOR))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/agenda").param("inicio", "2026-11-02").param("minha", "false")
                        .with(user(sessao(TipoUsuario.TUTOR))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agendaService);
    }

    @Test
    @DisplayName("sem sessao, a agenda manda para o login")
    void semSessaoVaiParaOLogin() throws Exception {
        mockMvc.perform(get("/agenda").param("inicio", "2026-11-02"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(agendaService);
    }

    private static ClinicaUserDetails sessao(TipoUsuario tipo) {
        return new ClinicaUserDetails(
                new AuthenticatedUser(21L, tipo.name().toLowerCase() + "@clinica.com",
                        "Usuario de teste", tipo, 47L),
                "irrelevante");
    }
}
