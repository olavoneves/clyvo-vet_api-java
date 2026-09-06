package br.com.clyvovet.server.agente.llm;

import br.com.clyvovet.server.agente.AgenteDesligadoException;
import br.com.clyvovet.server.agente.AgenteProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fala com a API de mensagens da Anthropic. Provedor alternativo.
 *
 * <p>Foi o provedor original deste modulo e continua inteiro, atras de
 * {@code app.agente.provedor=anthropic}. Nao esta aqui por nostalgia: e o que
 * torna a porta verificavel. Uma interface com uma implementacao so e uma
 * hipotese sobre desacoplamento; com duas, o teste que roda o mesmo laco contra
 * os dois formatos de fio prova que o {@code AgenteService} nao sabe quem esta
 * atras.
 *
 * <p>Diferenca de forma que o adaptador absorve: aqui o resultado de ferramenta
 * viaja como <b>texto</b> no campo {@code content}, e a chamada e o resultado
 * casam por {@code tool_use_id}. No Gemini os dois casam por nome e o resultado
 * e um objeto. O vocabulario neutro carrega id e nome justamente para servir aos
 * dois.
 */
@Slf4j
public class AnthropicAdapter implements ProvedorLlm {

    public static final String NOME = "anthropic";

    private static final String CAMINHO_MENSAGENS = "/v1/messages";
    private static final String CABECALHO_CHAVE = "x-api-key";
    private static final String CABECALHO_VERSAO = "anthropic-version";
    private static final String VERSAO_DA_API = "2023-06-01";

    /** Conectar e barato; o que demora e o modelo pensar. */
    private static final Duration TIMEOUT_DE_CONEXAO = Duration.ofSeconds(10);

    private final RestClient rest;
    private final AgenteProperties propriedades;
    private final ObjectMapper objectMapper;

    public AnthropicAdapter(RestClient rest, AgenteProperties propriedades, ObjectMapper objectMapper) {
        this.rest = rest;
        this.propriedades = propriedades;
        this.objectMapper = objectMapper;
    }

    /** Endereco e credenciais. Nao toca no transporte. */
    public static RestClient.Builder identificar(RestClient.Builder builder,
                                                 AgenteProperties propriedades) {
        AgenteProperties.Provedor anthropic = propriedades.anthropic();
        return builder
                .baseUrl(anthropic.urlBase())
                .defaultHeader(CABECALHO_CHAVE,
                        anthropic.chaveApi() == null ? "" : anthropic.chaveApi())
                .defaultHeader(CABECALHO_VERSAO, VERSAO_DA_API);
    }

    /**
     * O RestClient de producao: identidade mais transporte com timeout.
     *
     * <p>As duas metades sao separadas para que o teste monte a sua com o stub no
     * lugar do transporte, sem perder os cabecalhos — que o teste tambem precisa
     * verificar.
     */
    public static RestClient configurar(RestClient.Builder builder, AgenteProperties propriedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT_DE_CONEXAO);
        fabrica.setReadTimeout(propriedades.timeout());

        return identificar(builder, propriedades).requestFactory(fabrica).build();
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public boolean disponivel() {
        return propriedades.anthropic().disponivel();
    }

    @Override
    public DialogoLlm.Turno responder(String promptDoSistema,
                                      List<DialogoLlm.Mensagem> historico,
                                      List<DialogoLlm.Ferramenta> ferramentas) {
        if (!disponivel()) {
            throw new AgenteDesligadoException();
        }

        ProtocoloAnthropic.Requisicao requisicao = new ProtocoloAnthropic.Requisicao(
                propriedades.anthropic().modelo(),
                propriedades.maxTokens(),
                promptDoSistema,
                historico.stream().map(this::traduzir).toList(),
                declarar(ferramentas));

        ProtocoloAnthropic.Resposta resposta = ChamadaResiliente.executar(
                NOME, propriedades.maxRetentativas(), propriedades.timeout(),
                () -> rest.post()
                        .uri(CAMINHO_MENSAGENS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requisicao)
                        .retrieve()
                        .body(ProtocoloAnthropic.Resposta.class));

        return interpretar(resposta);
    }

    private static DialogoLlm.Turno interpretar(ProtocoloAnthropic.Resposta resposta) {
        List<DialogoLlm.Bloco.Chamada> chamadas = resposta.usosDeFerramenta().stream()
                .map(uso -> new DialogoLlm.Bloco.Chamada(uso.id(), uso.name(), uso.input()))
                .toList();

        return new DialogoLlm.Turno(resposta.textoConcatenado(), chamadas);
    }

    private ProtocoloAnthropic.Mensagem traduzir(DialogoLlm.Mensagem mensagem) {
        List<ProtocoloAnthropic.Bloco> blocos = new ArrayList<>();
        for (DialogoLlm.Bloco bloco : mensagem.blocos()) {
            blocos.add(traduzir(bloco));
        }

        return mensagem.papel() == DialogoLlm.Papel.AGENTE
                ? ProtocoloAnthropic.Mensagem.doAgente(blocos)
                : ProtocoloAnthropic.Mensagem.doTutor(blocos);
    }

    private ProtocoloAnthropic.Bloco traduzir(DialogoLlm.Bloco bloco) {
        return switch (bloco) {
            case DialogoLlm.Bloco.Texto texto ->
                    ProtocoloAnthropic.Bloco.texto(texto.texto());
            case DialogoLlm.Bloco.Chamada chamada ->
                    new ProtocoloAnthropic.Bloco(ProtocoloAnthropic.BLOCO_USO_DE_FERRAMENTA,
                            null, chamada.id(), chamada.nome(), chamada.argumentos(), null, null);
            case DialogoLlm.Bloco.Resultado resultado ->
                    ProtocoloAnthropic.Bloco.resultado(resultado.id(),
                            objectMapper.writeValueAsString(resultado.conteudo()));
        };
    }

    /**
     * As ferramentas como a API precisa recebe-las.
     *
     * <p>{@code strict} e ligado aqui, e nao no catalogo: e um recurso desta API
     * — ela passa a validar {@code input} contra o esquema antes de nos entregar
     * — e nao tem equivalente no Gemini. Uma flag de fornecedor no vocabulario
     * comum seria a primeira rachadura da porta.
     */
    private static List<ProtocoloAnthropic.Ferramenta> declarar(List<DialogoLlm.Ferramenta> ferramentas) {
        return ferramentas.stream()
                .map(f -> new ProtocoloAnthropic.Ferramenta(
                        f.nome(), f.descricao(), f.esquema(), Boolean.TRUE))
                .toList();
    }
}
