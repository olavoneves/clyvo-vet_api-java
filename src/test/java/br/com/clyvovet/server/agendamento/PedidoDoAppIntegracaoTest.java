package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.auth.ClinicaUserDetails;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.support.CenarioClinico;
import br.com.clyvovet.server.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O pedido do aplicativo respondido pela agenda: botao apertado, status no banco.
 *
 * <p>Vai pela cadeia de filtros das paginas, com sessao de veterinario e token
 * CSRF, e o tenant sai do principal pelo {@code TenantFilter} — como no
 * navegador. O status conferido e o que o aplicativo do tutor le para avisar:
 * CONFIRMADO, ou CANCELADO com o motivo.
 *
 * <p>Roda dentro da transacao do teste e e desfeito no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle configurado nas variaveis de ambiente")
class PedidoDoAppIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    private CenarioClinico.Cenario cenario;
    private Long pedido;
    private LocalDate hoje;

    @BeforeEach
    void montarPedidoDoApp() {
        CenarioClinico fixture = new CenarioClinico(entityManager);
        cenario = fixture.clinicaCompleta("Clinica do pedido pelo app");
        pedido = fixture.novoAgendamento(cenario.pet(), cenario.veterinario(), 3, "10:00",
                "SOLICITADO", "APP");
        hoje = LocalDate.now();
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Confirmar na agenda grava CONFIRMADO, o status que o app le como 'a clinica confirmou'")
    void confirmarPelaTela() throws Exception {
        mockMvc.perform(get("/agenda").param("inicio", hoje.toString()).with(user(sessaoDoVeterinario())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/agenda/" + pedido + "/confirmar")));

        mockMvc.perform(post("/agenda/" + pedido + "/confirmar")
                        .with(user(sessaoDoVeterinario()))
                        .with(csrf())
                        .param("inicio", hoje.toString())
                        .param("minha", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/agenda?inicio=" + hoje + "&minha=true"));

        assertThat(statusNoBanco()).isEqualTo("CONFIRMADO");

        // respondido, o pedido continua na agenda, mas sem os botoes
        mockMvc.perform(get("/agenda").param("inicio", hoje.toString()).with(user(sessaoDoVeterinario())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/agenda/" + pedido + "/confirmar"))));
    }

    @Test
    @DisplayName("Recusar com motivo grava CANCELADO e o motivo que o tutor le no app")
    void recusarPelaTela() throws Exception {
        mockMvc.perform(post("/agenda/" + pedido + "/recusar")
                        .with(user(sessaoDoVeterinario()))
                        .with(csrf())
                        .param("motivo", "  Veterinária em congresso nesse dia  ")
                        .param("inicio", hoje.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/agenda?inicio=" + hoje));

        assertThat(statusNoBanco()).isEqualTo("CANCELADO");
        assertThat(coluna("ds_motivo_cancelamento")).isEqualTo("Veterinária em congresso nesse dia");
    }

    @Test
    @DisplayName("Recusar sem motivo nao muda nada")
    void recusarSemMotivoNaoGrava() throws Exception {
        mockMvc.perform(post("/agenda/" + pedido + "/recusar")
                        .with(user(sessaoDoVeterinario()))
                        .with(csrf())
                        .param("motivo", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/agenda"));

        assertThat(statusNoBanco()).isEqualTo("SOLICITADO");
    }

    private String statusNoBanco() {
        return coluna("ds_status");
    }

    private String coluna(String nome) {
        entityManager.flush();
        entityManager.clear();
        return (String) entityManager.createNativeQuery(
                        "select " + nome + " from TB_CLV_AGENDAMENTO where id_agendamento = :id")
                .setParameter("id", pedido)
                .getSingleResult();
    }

    private ClinicaUserDetails sessaoDoVeterinario() {
        return new ClinicaUserDetails(
                new AuthenticatedUser(cenario.veterinario(), "vet@teste.local", "Vet de teste",
                        TipoUsuario.VETERINARIO, cenario.clinica()),
                "irrelevante");
    }
}
