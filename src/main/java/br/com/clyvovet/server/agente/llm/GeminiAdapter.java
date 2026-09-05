package br.com.clyvovet.server.agente.llm;

import br.com.clyvovet.server.agente.AgenteDesligadoException;
import br.com.clyvovet.server.agente.AgenteProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fala com a API do Gemini. Provedor ativo.
 *
 * <p>Escolhido por custo: o tier gratuito atende a demonstracao inteira sem
 * cartao. Essa escolha e tambem a razao de existir {@link ProvedorLlm} — um tier
 * gratuito pode mudar limite ou sumir, e quando isso acontecer trocar de
 * provedor tem que ser mudar {@code clyvo.agente.provedor}, e nao reescrever o
 * modulo.
 *
 * <p>RestClient direto, sem SDK, pelo mesmo motivo do adaptador da Anthropic: o
 * laco de execucao e nosso e esta no {@code AgenteService}; uma dependencia a
 * mais para montar o mesmo JSON so acrescentaria superficie de versao ao build.
 */
@Slf4j
public class GeminiAdapter implements ProvedorLlm {

    public static final String NOME = "gemini";

    /** O modelo entra no caminho, e nao no corpo: e assim que a API e desenhada. */
    private static final String CAMINHO = "/v1beta/models/{modelo}:generateContent";
    private static final String CABECALHO_CHAVE = "x-goog-api-key";

    /** Conectar e barato; o que demora e o modelo pensar. */
    private static final Duration TIMEOUT_DE_CONEXAO = Duration.ofSeconds(10);

    private final RestClient rest;
    private final AgenteProperties propriedades;

    public GeminiAdapter(RestClient rest, AgenteProperties propriedades) {
        this.rest = rest;
        this.propriedades = propriedades;
    }

    /** Endereco e credencial. Nao toca no transporte. */
    public static RestClient.Builder identificar(RestClient.Builder builder,
                                                 AgenteProperties propriedades) {
        AgenteProperties.Provedor gemini = propriedades.gemini();
        return builder
                .baseUrl(gemini.urlBase())
                .defaultHeader(CABECALHO_CHAVE,
                        gemini.chaveApi() == null ? "" : gemini.chaveApi());
    }

    /**
     * O RestClient de producao: identidade mais transporte com timeout.
     *
     * <p>As duas metades sao separadas para que o teste monte a sua com o stub no
     * lugar do transporte, sem perder o cabecalho da chave — que o teste tambem
     * precisa verificar. Um stub que nao exige a chave nao provaria nada sobre
     * como autenticamos.
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
        return propriedades.gemini().disponivel();
    }

    @Override
    public DialogoLlm.Turno responder(String promptDoSistema,
                                      List<DialogoLlm.Mensagem> historico,
                                      List<DialogoLlm.Ferramenta> ferramentas) {
        if (!disponivel()) {
            throw new AgenteDesligadoException();
        }

        ProtocoloGemini.Requisicao requisicao = new ProtocoloGemini.Requisicao(
                ProtocoloGemini.Conteudo.instrucao(promptDoSistema),
                historico.stream().map(GeminiAdapter::traduzir).toList(),
                declarar(ferramentas),
                ProtocoloGemini.ConfiguracaoDeGeracao.semPensar(propriedades.maxTokens()));

        ProtocoloGemini.Resposta resposta = ChamadaResiliente.executar(
                NOME, propriedades.maxRetentativas(), propriedades.timeout(),
                () -> rest.post()
                        .uri(CAMINHO, propriedades.gemini().modelo())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requisicao)
                        .retrieve()
                        .body(ProtocoloGemini.Resposta.class));

        return interpretar(resposta);
    }

    /**
     * A resposta do Gemini no vocabulario neutro.
     *
     * <p>O que decide se o turno acabou e a presenca de {@code functionCall}
     * entre as partes — nunca o {@code finishReason}, que diz {@code STOP} tanto
     * quando o modelo terminou de falar quanto quando ele parou para chamar uma
     * ferramenta.
     */
    private static DialogoLlm.Turno interpretar(ProtocoloGemini.Resposta resposta) {
        List<String> textos = new ArrayList<>();
        List<DialogoLlm.Bloco.Chamada> chamadas = new ArrayList<>();

        List<ProtocoloGemini.Parte> partes = resposta.partes();
        for (int i = 0; i < partes.size(); i++) {
            ProtocoloGemini.Parte parte = partes.get(i);

            if (parte.text() != null && !parte.text().isBlank()) {
                textos.add(parte.text());
            }
            if (parte.functionCall() != null) {
                // o Gemini nao emite id; o laco precisa de um para parear chamada
                // e resultado, entao ele nasce aqui e morre na traducao de volta
                chamadas.add(new DialogoLlm.Bloco.Chamada(
                        NOME + "-" + i,
                        parte.functionCall().name(),
                        parte.functionCall().args(),
                        parte.thoughtSignature()));
            }
        }

        return new DialogoLlm.Turno(String.join("\n\n", textos), chamadas);
    }

    /** Uma mensagem do vocabulario neutro no formato de fio do Gemini. */
    private static ProtocoloGemini.Conteudo traduzir(DialogoLlm.Mensagem mensagem) {
        List<ProtocoloGemini.Parte> partes = mensagem.blocos().stream()
                .map(GeminiAdapter::traduzir)
                .toList();

        String papel = mensagem.papel() == DialogoLlm.Papel.AGENTE
                ? ProtocoloGemini.PAPEL_MODELO
                : ProtocoloGemini.PAPEL_TUTOR;

        return ProtocoloGemini.Conteudo.de(papel, partes);
    }

    private static ProtocoloGemini.Parte traduzir(DialogoLlm.Bloco bloco) {
        return switch (bloco) {
            case DialogoLlm.Bloco.Texto texto ->
                    ProtocoloGemini.Parte.texto(texto.texto());
            case DialogoLlm.Bloco.Chamada chamada ->
                    ProtocoloGemini.Parte.chamada(
                            new ProtocoloGemini.ChamadaDeFuncao(chamada.nome(), chamada.argumentos()),
                            chamada.assinatura());
            case DialogoLlm.Bloco.Resultado resultado ->
                    ProtocoloGemini.Parte.resposta(resultado.nome(), resultado.conteudo());
        };
    }

    private static List<ProtocoloGemini.Ferramentas> declarar(List<DialogoLlm.Ferramenta> ferramentas) {
        if (ferramentas.isEmpty()) {
            return null;
        }
        return List.of(new ProtocoloGemini.Ferramentas(ferramentas.stream()
                .map(f -> new ProtocoloGemini.Declaracao(
                        f.nome(), f.descricao(), semCamposNaoSuportados(f.esquema())))
                .toList()));
    }

    /**
     * O esquema da ferramenta podado para o que o Gemini aceita.
     *
     * <p>{@code parameters} nao e JSON Schema completo — e um subconjunto do
     * OpenAPI 3.0, e campo desconhecido faz a requisicao inteira voltar 400. O
     * caso concreto e {@code additionalProperties}, que os esquemas deste projeto
     * emitem para fechar o objeto e que aqui precisa sair.
     *
     * <p>Perder essa restricao nao afrouxa a validacao de verdade: quem recusa
     * argumento faltando ou de tipo errado e o proprio codigo da ferramenta, em
     * {@code Esquemas.inteiroDe} e {@code textoDe}, e a excecao vira resultado
     * que o modelo le e corrige na volta seguinte. Essa rede vale para qualquer
     * provedor, e nao so para o que declara esquema estrito.
     */
    private static Map<String, Object> semCamposNaoSuportados(Map<String, Object> esquema) {
        Map<String, Object> podado = new LinkedHashMap<>();

        esquema.forEach((chave, valor) -> {
            if ("additionalProperties".equals(chave)) {
                return;
            }
            if (valor instanceof Map<?, ?> aninhado) {
                @SuppressWarnings("unchecked")
                Map<String, Object> convertido = (Map<String, Object>) aninhado;
                podado.put(chave, semCamposNaoSuportados(convertido));
            } else {
                podado.put(chave, valor);
            }
        });

        return podado;
    }
}
