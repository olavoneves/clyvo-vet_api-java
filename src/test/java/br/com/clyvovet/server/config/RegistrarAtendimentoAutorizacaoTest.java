package br.com.clyvovet.server.config;

import br.com.clyvovet.server.anamnese.AnamneseService;
import br.com.clyvovet.server.atendimento.CatalogoDeContextos;
import br.com.clyvovet.server.atendimento.RegistroDeAtendimentoService;
import br.com.clyvovet.server.atendimento.web.AtendimentoWebController;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.auth.ClinicaUserDetails;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.consulta.ConsultaController;
import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.enums.PetPorte;
import br.com.clyvovet.server.enums.PetSexo;
import br.com.clyvovet.server.enums.PetStatus;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.exame.ExameService;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.pet.PetFichaTecnicaResponse;
import br.com.clyvovet.server.pet.PetService;
import br.com.clyvovet.server.prescricao.PrescricaoService;
import br.com.clyvovet.server.veterinario.VeterinarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem alcanca a tela "Registrar atendimento".
 *
 * <p>A tela e a mesma operacao de {@code POST /api/consultas} seguido de
 * {@code POST /api/consultas/{id}/obrigacoes}, por outra porta. As duas portas
 * tem que abrir para os mesmos perfis: se a tela abrisse para alguem que a API
 * recusa, ela seria um atalho em volta da regra.
 *
 * <p>A cadeia das paginas, diferente da API, nao termina em {@code denyAll}:
 * termina em {@code authenticated()}. A rota so fica fechada para o tutor
 * porque mora sob {@code /pets/**}, que {@code PAGINAS_DA_CLINICA} reserva para
 * a equipe. E isso que este teste trava — mover a rota para fora de
 * {@code /pets} a abriria para o tutor sem nenhum erro de compilacao.
 */
@WebMvcTest(controllers = {
        AtendimentoWebController.class,
        ConsultaController.class
})
@Import({SecurityConfig.class, WebMvcConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-longo-o-bastante-para-hmac-sha-384-clyvovet",
        "app.jwt.access-token-expiration-ms=900000"
})
class RegistrarAtendimentoAutorizacaoTest {

    private static final String FORMULARIO = "/pets/9/atendimentos/novo";
    private static final String ENVIO = "/pets/9/atendimentos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PetService petService;

    @MockitoBean
    private VeterinarioService veterinarioService;

    @MockitoBean
    private RegistroDeAtendimentoService registroService;

    @MockitoBean
    private CatalogoDeContextos catalogoDeContextos;

    @MockitoBean
    private ConsultaService consultaService;

    @MockitoBean
    private ObrigacaoService obrigacaoService;

    @MockitoBean
    private AnamneseService anamneseService;

    @MockitoBean
    private PrescricaoService prescricaoService;

    @MockitoBean
    private ExameService exameService;

    @Test
    @DisplayName("veterinario e colaborador abrem o formulario")
    void aEquipeAbreOFormulario() throws Exception {
        given(petService.getFichaTecnica(9L)).willReturn(ficha());
        given(veterinarioService.findAll(any(Pageable.class))).willReturn(Page.empty());

        for (TipoUsuario tipo : new TipoUsuario[]{TipoUsuario.VETERINARIO, TipoUsuario.COLABORADOR}) {
            mockMvc.perform(get(FORMULARIO).with(user(sessao(tipo))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("tutor nao abre o formulario nem registra atendimento")
    void tutorNaoRegistraAtendimento() throws Exception {
        mockMvc.perform(get(FORMULARIO).with(user(sessao(TipoUsuario.TUTOR))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ENVIO).with(user(sessao(TipoUsuario.TUTOR))).with(csrf())
                        .param("veterinarioId", "21")
                        .param("dtConsulta", "2026-10-28")
                        .param("eventoGatilho", "CONSULTA_REGISTRADA"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registroService, petService);
    }

    @Test
    @DisplayName("sem sessao, a tela manda para o login")
    void semSessaoVaiParaOLogin() throws Exception {
        mockMvc.perform(get(FORMULARIO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    /** Sessao em cookie: sem o token CSRF o POST nao passa, nem para a equipe. */
    @Test
    @DisplayName("POST sem token CSRF e recusado")
    void postSemCsrfERecusado() throws Exception {
        mockMvc.perform(post(ENVIO).with(user(sessao(TipoUsuario.VETERINARIO)))
                        .param("veterinarioId", "21")
                        .param("dtConsulta", "2026-10-28")
                        .param("eventoGatilho", "CONSULTA_REGISTRADA"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registroService);
    }

    /** As duas portas da mesma operacao abrem e fecham para os mesmos perfis. */
    @Test
    @DisplayName("a tela e /api/consultas aceitam e recusam os mesmos perfis")
    void mesmosPerfisDaApi() throws Exception {
        given(petService.getFichaTecnica(9L)).willReturn(ficha());
        given(veterinarioService.findAll(any(Pageable.class))).willReturn(Page.empty());
        given(consultaService.findAll(any(Pageable.class))).willReturn(Page.empty());

        for (TipoUsuario tipo : TipoUsuario.values()) {
            int naTela = mockMvc.perform(get(FORMULARIO).with(user(sessao(tipo))))
                    .andReturn().getResponse().getStatus();
            int naApi = mockMvc.perform(get("/api/consultas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tipo)))
                    .andReturn().getResponse().getStatus();

            org.assertj.core.api.Assertions.assertThat(naTela == 403)
                    .as("perfil %s: tela respondeu %d e API respondeu %d", tipo, naTela, naApi)
                    .isEqualTo(naApi == 403);
        }
    }

    private static ClinicaUserDetails sessao(TipoUsuario tipo) {
        return new ClinicaUserDetails(
                new AuthenticatedUser(21L, tipo.name().toLowerCase() + "@clinica.com",
                        "Usuario de teste", tipo, 47L),
                "irrelevante");
    }

    private String bearer(TipoUsuario tipo) {
        return "Bearer " + jwtService.generateAccessToken(
                21L, tipo.name().toLowerCase() + "@clinica.com", "Usuario de teste", tipo, 47L);
    }

    private static PetFichaTecnicaResponse ficha() {
        return new PetFichaTecnicaResponse(9L, "Rex", LocalDate.of(2020, 3, 14), PetSexo.M,
                null, null, null, PetPorte.MEDIO, true, PetStatus.ATIVO, null, 3L,
                "Joana Ribeiro", "joana@exemplo.com", null, 5L, "Labrador", 1L, "Canina");
    }
}
