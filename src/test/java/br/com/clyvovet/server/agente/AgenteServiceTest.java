package br.com.clyvovet.server.agente;

import br.com.clyvovet.server.agendamento.AgendaService;
import br.com.clyvovet.server.agendamento.AgendamentoService;
import br.com.clyvovet.server.agente.ferramentas.ConsultarDisponibilidade;
import br.com.clyvovet.server.agente.ferramentas.CriarAgendamento;
import br.com.clyvovet.server.agente.ferramentas.EscalarParaVeterinario;
import br.com.clyvovet.server.agente.ferramentas.ListarObrigacoesPendentes;
import br.com.clyvovet.server.agente.ferramentas.Reagendar;
import br.com.clyvovet.server.agente.llm.GeminiAdapter;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.enums.ProtocoloCategoria;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.exception.ConflitoDeEstadoException;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.obrigacao.ObrigacaoDetalheResponse;
import br.com.clyvovet.server.obrigacao.ObrigacaoResponse;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * O laco do agente, contra um stub do Gemini — o provedor ativo.
 *
 * <p>Nenhuma chamada real, nenhuma cota consumida: o {@code MockRestServiceServer}
 * responde no lugar da API, com o mesmo JSON que ela devolveria. O que esta sob
 * teste e o que e nosso — o laco, o despacho de ferramenta, o tratamento de
 * conflito, o guardrail de saida e o isolamento por tutor.
 *
 * <p>O que <b>nao</b> esta sob teste, e nao pode estar com um stub, e a escolha
 * do modelo. Quando o teste de intencao afirma que "quero marcar" leva a
 * consultar_disponibilidade, o que ele verifica e que, tendo o modelo pedido
 * essa ferramenta, o agente a executa com os argumentos certos, devolve o
 * resultado no formato certo e continua a conversa. A qualidade da escolha em si
 * e assunto do prompt e de avaliacao com a API de verdade, nao de suite.
 *
 * <p>Que o mesmo laco funcione com o outro provedor e assunto de
 * {@link PortabilidadeDoProvedorTest}, que roda um cenario nos dois formatos de
 * fio. Aqui o formato e so o do Gemini, de proposito: e este que roda em
 * producao, e um teste que abstrai o formato deixaria de detectar uma quebra
 * nele.
 */
class AgenteServiceTest {

    private static final String STUB = "https://stub.gemini.local";
    private static final String MODELO = "gemini-2.5-flash";
    private static final String ROTA = STUB + "/v1beta/models/" + MODELO + ":generateContent";

    private static final Long CLINICA = 47L;
    private static final Long TUTOR = 3L;
    private static final Long PET = 9L;
    private static final Long OBRIGACAO = 101L;

    private MockRestServiceServer api;
    private AgenteService agente;

    private ObrigacaoService obrigacaoService;
    private AgendaService agendaService;
    private AgendamentoService agendamentoService;
    private PetRepository petRepository;
    private RegistroDeConversa registro;

    @BeforeEach
    void montar() {
        montarCom(propriedades("chave-de-teste"));

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

    private void montarCom(AgenteProperties propriedades) {
        obrigacaoService = mock(ObrigacaoService.class);
        agendaService = mock(AgendaService.class);
        agendamentoService = mock(AgendamentoService.class);
        petRepository = mock(PetRepository.class);
        registro = mock(RegistroDeConversa.class);

        given(petRepository.idDoTutor(PET)).willReturn(Optional.of(TUTOR));
        given(petRepository.nomeDoPet(PET)).willReturn(Optional.of("Rex"));

        RestClient.Builder builder = RestClient.builder();
        api = MockRestServiceServer.bindTo(builder).build();

        GeminiAdapter provedor = new GeminiAdapter(
                GeminiAdapter.identificar(builder, propriedades).build(), propriedades);

        CatalogoDeFerramentas catalogo = new CatalogoDeFerramentas(List.of(
                new ListarObrigacoesPendentes(obrigacaoService),
                new ConsultarDisponibilidade(agendaService),
                new CriarAgendamento(agendaService, obrigacaoService),
                new Reagendar(agendaService, agendamentoService),
                new EscalarParaVeterinario()));

        agente = new AgenteService(propriedades, provedor, catalogo,
                new MemoriaDeConversa(propriedades), new GuardrailClinico(), registro,
                petRepository);
    }

    // ---------- formato de fio do Gemini ----------

    /**
     * A requisicao tem que sair no formato que a API do Gemini entende.
     *
     * <p>O laco nao consegue provar isto sozinho: um adaptador que montasse o
     * JSON errado ainda passaria em todos os testes de comportamento, porque o
     * stub responde o que mandarmos independentemente do que recebeu. Este teste
     * olha o corpo que saiu.
     */
    @Test
    @DisplayName("a requisicao sai no formato do Gemini: contents, systemInstruction e functionDeclarations")
    void requisicaoSaiNoFormatoDoGemini() {
        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andExpect(header("x-goog-api-key", "chave-de-teste"))
                .andExpect(content().string(containsString("\"systemInstruction\"")))
                .andExpect(content().string(containsString("\"contents\"")))
                .andExpect(content().string(containsString("\"functionDeclarations\"")))
                .andExpect(content().string(containsString("\"maxOutputTokens\":8192")))
                // o papel do tutor no Gemini e "user", e a ferramenta e declarada pelo nome
                .andExpect(content().string(containsString("\"role\":\"user\"")))
                .andExpect(content().string(containsString("consultar_disponibilidade")))
                // additionalProperties nao existe no subconjunto de OpenAPI que o
                // Gemini aceita: se vazar, a API devolve 400 na demonstracao
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("additionalProperties"))))
                .andRespond(withSuccess(texto("Oi."), MediaType.APPLICATION_JSON));

        agente.responder(PET, "oi");
        api.verify();
    }

    // ---------- classificacao de intencao ----------

    @Test
    @DisplayName("\"quero marcar\" leva o agente a consultar a disponibilidade real")
    void queroMarcarLevaAConsultarDisponibilidade() {
        given(agendaService.horariosLivres(any(), any())).willReturn(List.of(
                new AgendaService.HorarioLivre(LocalDate.of(2026, 9, 17), "14:00", 5L, "Dra. Ana")));

        esperarChamada(chamadaDeFuncao("consultar_disponibilidade",
                "{\"dataInicio\":\"2026-09-14\",\"dataFim\":\"2026-09-21\"}"));
        esperarChamada(texto("Tenho quinta às 14:00 com a Dra. Ana. Serve?"));

        RespostaDoAgenteResponse resposta = agente.responder(PET, "quero marcar a vacina do Rex");

        api.verify();
        verify(agendaService).horariosLivres(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21));
        assertThat(resposta.texto()).contains("14:00");
        assertThat(resposta.escalado()).isFalse();
        assertThat(resposta.acoes()).isEmpty();
    }

    @Test
    @DisplayName("o resultado da ferramenta volta para a API como functionResponse")
    void resultadoDaFerramentaVoltaParaOModelo() {
        given(obrigacaoService.doPetComStatus(eq(PET), any(), any()))
                .willReturn(List.of(obrigacaoPendente()));

        esperarChamada(chamadaDeFuncao("listar_obrigacoes_pendentes", "{\"idPet\":9}"));

        // a segunda requisicao tem que carregar o que a ferramenta devolveu: sem
        // isso o modelo responderia no vazio e o laco seria decorativo
        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andExpect(content().string(containsString("\"functionResponse\"")))
                // o Gemini casa chamada e resposta pelo nome da funcao, nao por id
                .andExpect(content().string(containsString("listar_obrigacoes_pendentes")))
                .andExpect(content().string(containsString("Reforço anual")))
                .andRespond(withSuccess(texto("O reforço anual está pendente."),
                        MediaType.APPLICATION_JSON));

        RespostaDoAgenteResponse resposta = agente.responder(PET, "o que está pendente?");

        api.verify();
        assertThat(resposta.texto()).isEqualTo("O reforço anual está pendente.");
    }

    // ---------- agendamento ----------

    @Test
    @DisplayName("agendar produz a acao que a tela usa para recarregar as pendencias")
    void agendarProduzAcao() {
        given(obrigacaoService.findById(OBRIGACAO)).willReturn(detalheDaObrigacao());
        given(agendaService.agendar(eq(OBRIGACAO), any(LocalDateTime.class), eq(5L)))
                .willReturn(new AgendaService.AgendamentoConfirmado(
                        777L, OBRIGACAO, LocalDate.of(2026, 9, 17), "14:00", 5L, "Dra. Ana", "Rex"));

        esperarChamada(chamadaDeFuncao("criar_agendamento",
                "{\"idObrigacao\":101,\"dataHora\":\"2026-09-17T14:00\",\"idVeterinario\":5}"));
        esperarChamada(texto("Marcado para 17/09 às 14:00 com a Dra. Ana."));

        RespostaDoAgenteResponse resposta = agente.responder(PET, "pode marcar quinta às 14h");

        api.verify();
        verify(agendaService).agendar(OBRIGACAO,
                LocalDateTime.of(2026, 9, 17, 14, 0), 5L);
        assertThat(resposta.acoes()).singleElement()
                .satisfies(acao -> {
                    assertThat(acao.tipo()).isEqualTo(AcaoDoAgente.AGENDAMENTO_CRIADO);
                    assertThat(acao.id()).isEqualTo(777L);
                });
    }

    /**
     * Dois tutores disputando o mesmo horario.
     *
     * <p>O que o requisito pede nao e que o conflito nao aconteca — ele acontece,
     * e o {@code AgendaService} o detecta —, mas que ele seja tratado: o erro
     * volta para o modelo como resultado de ferramenta, e o turno continua.
     */
    @Test
    @DisplayName("horario ocupado no meio do caminho vira resultado de ferramenta, nao erro")
    void conflitoDeHorarioVoltaParaOModelo() {
        given(obrigacaoService.findById(OBRIGACAO)).willReturn(detalheDaObrigacao());
        willThrow(new ConflitoDeEstadoException("O horário de 14:00 acabou de ser ocupado."))
                .given(agendaService).agendar(anyLong(), any(LocalDateTime.class), anyLong());

        esperarChamada(chamadaDeFuncao("criar_agendamento",
                "{\"idObrigacao\":101,\"dataHora\":\"2026-09-17T14:00\",\"idVeterinario\":5}"));

        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andExpect(content().string(containsString("acabou de ser ocupado")))
                .andRespond(withSuccess(texto("Esse horário foi ocupado. Tenho 15:30 no mesmo dia."),
                        MediaType.APPLICATION_JSON));

        RespostaDoAgenteResponse resposta = agente.responder(PET, "pode marcar quinta às 14h");

        api.verify();
        assertThat(resposta.texto()).contains("15:30");
        assertThat(resposta.escalado()).isFalse();
        assertThat(resposta.acoes()).isEmpty();
    }

    // ---------- guardrail clinico ----------

    @Test
    @DisplayName("o modelo responde a pergunta clinica mesmo assim, e a saida e barrada")
    void perguntaClinicaRespondidaMesmoAssimEBarrada() {
        // o caso que justifica a segunda camada: a instrucao do prompt falhou.
        // Trocar de provedor nao muda nada aqui — e exatamente o ponto: o
        // guardrail le a resposta final, e nao o formato de quem a produziu
        esperarChamada(texto("Pode dar 250 mg de dipirona a cada 8 horas, não é grave."));

        RespostaDoAgenteResponse resposta = agente.responder(PET, "ele está mancando, o que dou?");

        api.verify();
        assertThat(resposta.texto()).isEqualTo(MensagensDoAgente.ESCALONAMENTO);
        assertThat(resposta.texto()).doesNotContain("dipirona");
        assertThat(resposta.escalado()).isTrue();
        assertThat(resposta.acoes()).extracting(AcaoDoAgente::tipo)
                .containsExactly(AcaoDoAgente.ESCALONAMENTO);

        // o evento fica registrado com a categoria barrada, para auditoria
        verify(registro).registrar(any(), any(), eq(MensagensDoAgente.ESCALONAMENTO),
                any(), any(), eq(true), eq("dosagem"));
    }

    @Test
    @DisplayName("a resposta barrada nao volta para a API no turno seguinte")
    void respostaBarradaSaiDoHistorico() {
        // as duas expectativas vao antes das chamadas: o servidor de teste nao
        // aceita novas depois que a primeira requisicao ja passou
        esperarChamada(texto("Pode dar 250 mg de dipirona a cada 8 horas."));

        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("dipirona"))))
                .andRespond(withSuccess(texto("Posso marcar uma consulta."), MediaType.APPLICATION_JSON));

        agente.responder(PET, "o que dou para ele?");
        agente.responder(PET, "e agora?");

        api.verify();
    }

    @Test
    @DisplayName("escalar_para_veterinario marca o turno como escalado")
    void escalarMarcaOTurno() {
        esperarChamada(chamadaDeFuncao("escalar_para_veterinario",
                "{\"idPet\":9,\"resumo\":\"Tutor relata que o pet está mancando.\"}"));
        esperarChamada(texto("Encaminhei para a equipe clínica. Um veterinário vai te responder."));

        RespostaDoAgenteResponse resposta = agente.responder(PET, "ele está mancando");

        api.verify();
        assertThat(resposta.escalado()).isTrue();
        assertThat(resposta.acoes()).extracting(AcaoDoAgente::tipo)
                .contains(AcaoDoAgente.ESCALONAMENTO);
    }

    // ---------- isolamento ----------

    @Test
    @DisplayName("tutor nao conversa sobre pet de outra clinica nem de outro dono")
    void tutorNaoAlcancaPetDeOutroDono() {
        // o filtro de tenant faz o pet de outra clinica nao existir (Optional vazio);
        // aqui o caso mais sutil, que ele nao cobre: pet da mesma clinica, outro dono
        given(petRepository.idDoTutor(42L)).willReturn(Optional.of(999L));

        assertThatThrownBy(() -> agente.responder(42L, "quero marcar"))
                .isInstanceOf(EntityNotFoundException.class);

        // nem a API nem o registro chegam a ser tocados
        api.verify();
        verify(registro, never()).registrar(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("pet inexistente para esta clinica tambem e 404")
    void petDeOutraClinicaNaoExiste() {
        given(petRepository.idDoTutor(4242L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> agente.responder(4242L, "quero marcar"))
                .isInstanceOf(EntityNotFoundException.class);
        api.verify();
    }

    // ---------- resiliencia ----------

    @Test
    @DisplayName("sem GEMINI_API_KEY o servico recusa antes de qualquer trabalho")
    void semChaveRecusa() {
        montarCom(propriedades(""));

        assertThatThrownBy(() -> agente.responder(PET, "quero marcar"))
                .isInstanceOf(AgenteDesligadoException.class);
        api.verify();
    }

    @Test
    @DisplayName("modelo preso em ferramenta esgota o limite e escala")
    void limiteDeIteracoesEscala() {
        given(obrigacaoService.doPetComStatus(eq(PET), any(), any())).willReturn(List.of());

        api.expect(ExpectedCount.times(6), requestTo(ROTA))
                .andRespond(withSuccess(chamadaDeFuncao("listar_obrigacoes_pendentes", "{\"idPet\":9}"),
                        MediaType.APPLICATION_JSON));

        RespostaDoAgenteResponse resposta = agente.responder(PET, "e aí?");

        api.verify();
        assertThat(resposta.texto()).isEqualTo(MensagensDoAgente.SEM_CONCLUSAO);
        assertThat(resposta.escalado()).isTrue();
    }

    @Test
    @DisplayName("erro nao retentavel da API vira indisponibilidade, nao stack trace")
    void falhaDaApiViraMensagemDeIndisponibilidade() {
        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withBadRequest());

        RespostaDoAgenteResponse resposta = agente.responder(PET, "quero marcar");

        api.verify();
        assertThat(resposta.texto()).isEqualTo(MensagensDoAgente.INDISPONIVEL);
        assertThat(resposta.escalado()).isTrue();
    }

    /**
     * O 429 deixa de ser excecao num tier gratuito: e a metade mais exercitada
     * da politica de retentativa, e a que sustenta a escolha do provedor.
     */
    @Test
    @DisplayName("429 do tier gratuito e retentado; a segunda tentativa vale")
    void limiteDeCotaERetentado() {
        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withTooManyRequests());
        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andRespond(withSuccess(texto("Tudo certo."), MediaType.APPLICATION_JSON));

        assertThat(agente.responder(PET, "quero marcar").texto()).isEqualTo("Tudo certo.");
        api.verify();
    }

    @Test
    @DisplayName("5xx e retentado; a segunda tentativa vale")
    void erroDeServidorERetentado() {
        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withServerError());
        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andRespond(withSuccess(texto("Tudo certo."), MediaType.APPLICATION_JSON));

        assertThat(agente.responder(PET, "quero marcar").texto()).isEqualTo("Tudo certo.");
        api.verify();
    }

    // ---------- historico ----------

    @Test
    void historicoMostraApenasOQueFoiDito() {
        given(obrigacaoService.doPetComStatus(eq(PET), any(), any())).willReturn(List.of());

        esperarChamada(chamadaDeFuncao("listar_obrigacoes_pendentes", "{\"idPet\":9}"));
        esperarChamada(texto("Nada pendente."));

        agente.responder(PET, "o que falta?");
        ConversaResponse historico = agente.historico(PET);

        assertThat(historico.idPet()).isEqualTo(PET);
        assertThat(historico.escalada()).isFalse();
        assertThat(historico.turnos()).extracting(ConversaResponse.Turno::texto)
                .containsExactly("o que falta?", "Nada pendente.");
    }

    // ---------- apoio ----------

    private void esperarChamada(String corpoDaResposta) {
        api.expect(ExpectedCount.once(), requestTo(ROTA))
                .andExpect(header("x-goog-api-key", "chave-de-teste"))
                .andRespond(withSuccess(corpoDaResposta, MediaType.APPLICATION_JSON));
    }

    private static AgenteProperties propriedades(String chave) {
        return new AgenteProperties(6, 8192, Duration.ofSeconds(30), 2, Duration.ofHours(24),
                null, new AgenteProperties.Provedor(chave, MODELO, STUB));
    }

    /** Resposta de texto do Gemini: uma parte {@code text} no primeiro candidato. */
    private static String texto(String conteudo) {
        return """
                {"candidates":[{"content":{"role":"model",
                 "parts":[{"text":"%s"}]},"finishReason":"STOP"}],
                 "modelVersion":"gemini-2.5-flash"}
                """.formatted(conteudo.replace("\"", "\\\""));
    }

    /**
     * Pedido de ferramenta do Gemini.
     *
     * <p>Note que {@code finishReason} continua {@code STOP}: nao ha equivalente
     * ao {@code stop_reason: tool_use} da Anthropic. O que diz que o modelo quer
     * uma ferramenta e a presenca da parte {@code functionCall}, e e isso que o
     * adaptador tem que olhar.
     */
    private static String chamadaDeFuncao(String nome, String argumentos) {
        return """
                {"candidates":[{"content":{"role":"model",
                 "parts":[{"functionCall":{"name":"%s","args":%s}}]},"finishReason":"STOP"}],
                 "modelVersion":"gemini-2.5-flash"}
                """.formatted(nome, argumentos);
    }

    private static ObrigacaoResponse obrigacaoPendente() {
        return new ObrigacaoResponse(OBRIGACAO, PET, "Rex", 4L, "Reforço anual",
                "VAC-V10", "Vacinação V10", ProtocoloCategoria.VACINA, ObrigacaoStatus.PREVISTA,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 25),
                false, new BigDecimal("180.00"), null);
    }

    private static ObrigacaoDetalheResponse detalheDaObrigacao() {
        return new ObrigacaoDetalheResponse(obrigacaoPendente(), "corr-1",
                null, null, null, null, List.of());
    }

}
