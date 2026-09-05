package br.com.clyvovet.server.agente;

import br.com.clyvovet.server.agendamento.AgendaService;
import br.com.clyvovet.server.agendamento.AgendamentoService;
import br.com.clyvovet.server.agente.ferramentas.ConsultarDisponibilidade;
import br.com.clyvovet.server.agente.ferramentas.CriarAgendamento;
import br.com.clyvovet.server.agente.ferramentas.EscalarParaVeterinario;
import br.com.clyvovet.server.agente.ferramentas.ListarObrigacoesPendentes;
import br.com.clyvovet.server.agente.ferramentas.Reagendar;
import br.com.clyvovet.server.agente.llm.AnthropicAdapter;
import br.com.clyvovet.server.agente.llm.GeminiAdapter;
import br.com.clyvovet.server.agente.llm.ProvedorLlm;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.pet.PetRepository;
import br.com.clyvovet.server.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * A porta {@code ProvedorLlm} cumpre o que promete: o laco nao sabe quem esta
 * atras.
 *
 * <p>Este e o teste que justifica a existencia da porta. Uma interface com uma
 * implementacao so e uma hipotese sobre desacoplamento — qualquer vazamento do
 * formato de fio para dentro do {@code AgenteService} continuaria passando em
 * todos os outros testes. Aqui o mesmo cenario roda contra os dois provedores,
 * com dois JSONs de fio genuinamente diferentes, e o resultado observavel tem
 * que ser identico.
 *
 * <p>As diferencas que os adaptadores absorvem, e que este teste exercita:
 *
 * <ul>
 *   <li>papel do modelo: {@code assistant} contra {@code model};
 *   <li>pedido de ferramenta: {@code stop_reason: tool_use} com bloco
 *       {@code tool_use} contra a mera presenca de uma parte
 *       {@code functionCall};
 *   <li>resultado: texto em {@code tool_result} casado por {@code tool_use_id}
 *       contra objeto em {@code functionResponse} casado por nome.
 * </ul>
 *
 * <p>Nenhuma chamada real, nenhuma cota consumida nos dois casos.
 */
class PortabilidadeDoProvedorTest {

    private static final Long CLINICA = 47L;
    private static final Long TUTOR = 3L;
    private static final Long PET = 9L;

    /** Um token opaco qualquer: o que importa e sair identico ao que entrou. */
    private static final String ASSINATURA = "EtsMCtgMARFNMg+jcu3ug3m2dnuOs66mDg";

    private ObrigacaoService obrigacaoService;
    private AgendaService agendaService;
    private PetRepository petRepository;
    private RegistroDeConversa registro;

    @BeforeEach
    void autenticar() {
        TenantContext.set(CLINICA);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(TUTOR, "joana@exemplo.com", "Joana",
                                TipoUsuario.TUTOR, CLINICA),
                        null, List.of()));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---------- o mesmo ciclo, nos dois provedores ----------

    @Test
    @DisplayName("Gemini: consulta a agenda e responde com o horario real")
    void cicloCompletoNoGemini() {
        cicloCompleto(gemini());
    }

    @Test
    @DisplayName("Anthropic: consulta a agenda e responde com o horario real")
    void cicloCompletoNaAnthropic() {
        cicloCompleto(anthropic());
    }

    @Test
    @DisplayName("Gemini: o guardrail clinico barra a resposta final")
    void guardrailNoGemini() {
        guardrailBarraIndependenteDoProvedor(gemini());
    }

    /**
     * O requisito explicito: o guardrail e a camada que nao pode depender de qual
     * modelo esta atras. Ele roda no {@code AgenteService}, sobre a resposta ja
     * traduzida — nunca dentro de um adaptador, onde um provedor novo poderia
     * esquecer de aplica-lo.
     */
    @Test
    @DisplayName("Anthropic: o guardrail clinico barra a resposta final")
    void guardrailNaAnthropic() {
        guardrailBarraIndependenteDoProvedor(anthropic());
    }

    /**
     * A assinatura que acompanha a chamada de ferramenta volta intacta na
     * requisicao seguinte.
     *
     * <p>Nao e zelo: a familia Gemini 3.x recusa a requisicao inteira com 400 e
     * {@code Function call is missing a thought_signature} quando a chamada
     * reaparece no historico sem o token que a acompanhava. Como o
     * {@code gemini-2.5-flash} saiu do ar para chaves novas, todo modelo
     * disponivel exige isso — e sem este teste o laco volta a quebrar na segunda
     * volta, que e justamente onde nenhum teste de uma volta so olha.
     */
    @Test
    @DisplayName("Gemini: a assinatura da chamada reaparece na volta seguinte")
    void assinaturaDaChamadaVoltaNoHistorico() {
        Fixture fixture = gemini();
        Montagem montagem = montar(fixture);

        given(agendaService.horariosLivres(any(), any())).willReturn(List.of(
                new AgendaService.HorarioLivre(LocalDate.of(2026, 9, 17), "14:00", 5L, "Dra. Ana")));

        montagem.api.expect(ExpectedCount.once(), requestTo(fixture.rota))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"role":"model","parts":[
                         {"functionCall":{"name":"consultar_disponibilidade",
                           "args":{"dataInicio":"2026-09-14","dataFim":"2026-09-21"}},
                          "thoughtSignature":"%s"}]},"finishReason":"STOP"}]}
                        """.formatted(ASSINATURA), MediaType.APPLICATION_JSON));

        montagem.api.expect(ExpectedCount.once(), requestTo(fixture.rota))
                .andExpect(content().string(containsString(ASSINATURA)))
                .andRespond(withSuccess(fixture.texto.apply("Tenho quinta às 14:00 com a Dra. Ana."),
                        MediaType.APPLICATION_JSON));

        montagem.agente.responder(PET, "quero marcar");

        montagem.api.verify();
    }

    /**
     * O contraponto: a Anthropic nao tem token de continuacao, e nada do
     * vocabulario neutro pode vazar para o fio dela.
     */
    @Test
    @DisplayName("Anthropic: nenhuma assinatura vaza para o fio")
    void anthropicNaoEmiteAssinatura() {
        Fixture fixture = anthropic();
        Montagem montagem = montar(fixture);

        given(agendaService.horariosLivres(any(), any())).willReturn(List.of(
                new AgendaService.HorarioLivre(LocalDate.of(2026, 9, 17), "14:00", 5L, "Dra. Ana")));

        montagem.responder(fixture.chamada.apply("consultar_disponibilidade",
                "{\"dataInicio\":\"2026-09-14\",\"dataFim\":\"2026-09-21\"}"));

        montagem.api.expect(ExpectedCount.once(), requestTo(fixture.rota))
                .andExpect(content().string(not(containsString("thoughtSignature"))))
                .andExpect(content().string(not(containsString("assinatura"))))
                .andRespond(withSuccess(fixture.texto.apply("Tenho quinta às 14:00 com a Dra. Ana."),
                        MediaType.APPLICATION_JSON));

        montagem.agente.responder(PET, "quero marcar");

        montagem.api.verify();
    }

    // ---------- os dois cenarios, escritos uma vez so ----------

    private void cicloCompleto(Fixture fixture) {
        Montagem montagem = montar(fixture);

        given(agendaService.horariosLivres(any(), any())).willReturn(List.of(
                new AgendaService.HorarioLivre(LocalDate.of(2026, 9, 17), "14:00", 5L, "Dra. Ana")));

        montagem.responder(fixture.chamada.apply("consultar_disponibilidade",
                "{\"dataInicio\":\"2026-09-14\",\"dataFim\":\"2026-09-21\"}"));
        montagem.responder(fixture.texto.apply("Tenho quinta às 14:00 com a Dra. Ana."));

        RespostaDoAgenteResponse resposta = montagem.agente.responder(PET, "quero marcar");

        montagem.api.verify();
        verify(agendaService).horariosLivres(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21));
        assertThat(resposta.texto())
                .as("o texto final tem que ser o mesmo, venha de qual provedor vier")
                .isEqualTo("Tenho quinta às 14:00 com a Dra. Ana.");
        assertThat(resposta.escalado()).isFalse();
    }

    private void guardrailBarraIndependenteDoProvedor(Fixture fixture) {
        Montagem montagem = montar(fixture);

        montagem.responder(fixture.texto.apply("Pode dar 250 mg de dipirona a cada 8 horas."));

        RespostaDoAgenteResponse resposta = montagem.agente.responder(PET, "o que dou para ele?");

        montagem.api.verify();
        assertThat(resposta.texto()).isEqualTo(MensagensDoAgente.ESCALONAMENTO);
        assertThat(resposta.texto()).doesNotContain("dipirona");
        assertThat(resposta.escalado()).isTrue();
        verify(registro).registrar(any(), any(), org.mockito.ArgumentMatchers.eq(MensagensDoAgente.ESCALONAMENTO),
                any(), any(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("dosagem"));
    }

    // ---------- montagem ----------

    /** O que muda de um provedor para o outro, e nada alem disto. */
    private record Fixture(String rota,
                           UnaryOperator<String> texto,
                           BiFunction<String, String, String> chamada,
                           BiFunction<RestClient.Builder, AgenteProperties, ProvedorLlm> provedor,
                           AgenteProperties propriedades) {
    }

    private record Montagem(MockRestServiceServer api, AgenteService agente, String rota) {

        void responder(String corpo) {
            api.expect(ExpectedCount.once(), requestTo(rota))
                    .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
        }
    }

    private Montagem montar(Fixture fixture) {
        obrigacaoService = mock(ObrigacaoService.class);
        agendaService = mock(AgendaService.class);
        AgendamentoService agendamentoService = mock(AgendamentoService.class);
        petRepository = mock(PetRepository.class);
        registro = mock(RegistroDeConversa.class);

        given(petRepository.idDoTutor(PET)).willReturn(Optional.of(TUTOR));
        given(petRepository.nomeDoPet(PET)).willReturn(Optional.of("Rex"));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer api = MockRestServiceServer.bindTo(builder).build();

        ProvedorLlm provedor = fixture.provedor.apply(builder, fixture.propriedades);

        CatalogoDeFerramentas catalogo = new CatalogoDeFerramentas(List.of(
                new ListarObrigacoesPendentes(obrigacaoService),
                new ConsultarDisponibilidade(agendaService),
                new CriarAgendamento(agendaService, obrigacaoService),
                new Reagendar(agendaService, agendamentoService),
                new EscalarParaVeterinario()));

        AgenteService agente = new AgenteService(fixture.propriedades, provedor, catalogo,
                new MemoriaDeConversa(fixture.propriedades), new GuardrailClinico(), registro,
                petRepository);

        return new Montagem(api, agente, fixture.rota);
    }

    // ---------- os dois formatos de fio ----------

    private static Fixture gemini() {
        String stub = "https://stub.gemini.local";
        String modelo = "gemini-2.5-flash";

        AgenteProperties propriedades = new AgenteProperties(
                6, 8192, Duration.ofSeconds(30), 2, Duration.ofHours(24),
                null, new AgenteProperties.Provedor("chave-gemini", modelo, stub));

        return new Fixture(
                stub + "/v1beta/models/" + modelo + ":generateContent",
                conteudo -> """
                        {"candidates":[{"content":{"role":"model",
                         "parts":[{"text":"%s"}]},"finishReason":"STOP"}]}
                        """.formatted(conteudo.replace("\"", "\\\"")),
                (nome, argumentos) -> """
                        {"candidates":[{"content":{"role":"model",
                         "parts":[{"functionCall":{"name":"%s","args":%s}}]},"finishReason":"STOP"}]}
                        """.formatted(nome, argumentos),
                (builder, props) -> new GeminiAdapter(
                        GeminiAdapter.identificar(builder, props).build(), props),
                propriedades);
    }

    private static Fixture anthropic() {
        String stub = "https://stub.anthropic.local";

        AgenteProperties propriedades = new AgenteProperties(
                6, 8192, Duration.ofSeconds(30), 2, Duration.ofHours(24),
                new AgenteProperties.Provedor("chave-anthropic", "claude-sonnet-4-6", stub),
                null);

        return new Fixture(
                stub + "/v1/messages",
                conteudo -> """
                        {"id":"msg_1","stop_reason":"end_turn",
                         "content":[{"type":"text","text":"%s"}]}
                        """.formatted(conteudo.replace("\"", "\\\"")),
                (nome, argumentos) -> """
                        {"id":"msg_1","stop_reason":"tool_use",
                         "content":[{"type":"tool_use","id":"toolu_1","name":"%s","input":%s}]}
                        """.formatted(nome, argumentos),
                (builder, props) -> new AnthropicAdapter(
                        AnthropicAdapter.identificar(builder, props).build(), props,
                        JsonMapper.builder().build()),
                propriedades);
    }
}
