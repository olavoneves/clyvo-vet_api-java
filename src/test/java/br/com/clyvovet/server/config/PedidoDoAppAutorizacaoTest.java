package br.com.clyvovet.server.config;

import br.com.clyvovet.server.agendamento.AgendamentoResponse;
import br.com.clyvovet.server.agendamento.AgendamentoService;
import br.com.clyvovet.server.agendamento.AgendamentoStatusRequest;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.auth.ClinicaUserDetails;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.enums.AgendamentoStatus;
import br.com.clyvovet.server.enums.CanalPreferencial;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.obrigacao.web.PedidoDoAppWebController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem responde ao pedido do aplicativo pela agenda.
 *
 * <p>Os mesmos perfis do {@code PATCH /api/agendamentos/{id}/status}: veterinario
 * e colaborador. A rota esta sob {@code /agenda/**}, que {@code PAGINAS_DA_CLINICA}
 * reserva para a equipe; o que se trava aqui e que o tutor, quem nao tem sessao e
 * um POST sem token CSRF nunca cheguem ao service — o tutor confirmando o proprio
 * pedido faria o aplicativo dizer que "a clinica confirmou".
 */
@WebMvcTest(controllers = PedidoDoAppWebController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-longo-o-bastante-para-hmac-sha-384-clyvovet",
        "app.jwt.access-token-expiration-ms=900000"
})
class PedidoDoAppAutorizacaoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgendamentoService agendamentoService;

    @Test
    @DisplayName("veterinario e colaborador confirmam e recusam")
    void aEquipeResponde() throws Exception {
        given(agendamentoService.updateStatus(eq(8L), any())).willReturn(resposta());

        for (TipoUsuario tipo : new TipoUsuario[]{TipoUsuario.VETERINARIO, TipoUsuario.COLABORADOR}) {
            mockMvc.perform(post("/agenda/8/confirmar").with(user(sessao(tipo))).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/agenda"));
            mockMvc.perform(post("/agenda/8/recusar").param("motivo", "Sem vaga")
                            .with(user(sessao(tipo))).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/agenda"));

            verify(agendamentoService).updateStatus(8L,
                    new AgendamentoStatusRequest(AgendamentoStatus.CONFIRMADO, null));
            verify(agendamentoService).updateStatus(8L,
                    new AgendamentoStatusRequest(AgendamentoStatus.CANCELADO, "Sem vaga"));
            clearInvocations(agendamentoService);
        }
    }

    @Test
    @DisplayName("tutor nao confirma nem recusa pedido")
    void tutorNaoResponde() throws Exception {
        mockMvc.perform(post("/agenda/8/confirmar").with(user(sessao(TipoUsuario.TUTOR))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/agenda/8/recusar").param("motivo", "Mudei de ideia")
                        .with(user(sessao(TipoUsuario.TUTOR))).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agendamentoService);
    }

    @Test
    @DisplayName("sem sessao, o botao manda para o login")
    void semSessaoVaiParaOLogin() throws Exception {
        mockMvc.perform(post("/agenda/8/confirmar").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(agendamentoService);
    }

    @Test
    @DisplayName("POST sem token CSRF e recusado mesmo para a equipe")
    void semCsrfNaoPassa() throws Exception {
        mockMvc.perform(post("/agenda/8/confirmar").with(user(sessao(TipoUsuario.VETERINARIO))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agendamentoService);
    }

    private static AgendamentoResponse resposta() {
        return new AgendamentoResponse(8L, LocalDate.of(2026, 11, 17), "09:00",
                AgendamentoStatus.CONFIRMADO, CanalPreferencial.APP, null,
                9L, "Nala", 21L, "Dra. Helena", null);
    }

    private static ClinicaUserDetails sessao(TipoUsuario tipo) {
        return new ClinicaUserDetails(
                new AuthenticatedUser(21L, tipo.name().toLowerCase() + "@clinica.com",
                        "Usuario de teste", tipo, 47L),
                "irrelevante");
    }
}
