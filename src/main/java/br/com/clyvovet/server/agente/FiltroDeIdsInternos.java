package br.com.clyvovet.server.agente;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Tira da resposta pronta os ids internos que o modelo deixou escapar.
 *
 * <p>Mesmo desenho do {@link GuardrailClinico}: a instrucao no prompt e a
 * primeira camada, e esta e a verificacao em codigo, porque instrucao nao e
 * controle. O caso que motivou foi real, no site: "reavaliação renal trimestral
 * (id 14273)". O id e do modelo e das ferramentas — ele precisa dele para
 * agendar —, nao do tutor.
 *
 * <p>Diferente do guardrail, aqui nao se barra a resposta inteira: um id a mais
 * nao torna a frase perigosa, so feia. Remove-se o trecho e o resto segue.
 *
 * <p>So sai o que vem marcado como id: a palavra "id" (ou "idObrigacao",
 * "código"...) colada a um numero, ou "#" seguido de dois digitos ou mais.
 * Numero solto fica — hora, data, quantidade de dias e numero de opcao sao
 * conversa de agenda, e um filtro que comesse "09:00" quebraria a confirmacao.
 */
@Component
public class FiltroDeIdsInternos {

    private static final int OPCOES = Pattern.CASE_INSENSITIVE
            | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    /**
     * "id", "idObrigacao", "id_agendamento", "código", "cod." seguidos do numero.
     *
     * <p>Os sufixos sao uma lista, e nao {@code id\w*}: "idade 3 anos" e "idoso"
     * tambem comecam por "id".
     */
    private static final String ROTULO_E_NUMERO =
            "\\b(?:id(?:[_ ]?(?:obriga[çc][ãa]o|agendamento|consulta|pet|veterin[áa]rio|vet))?"
                    + "|c[óo]d(?:igo|\\.)?)\\s*(?:[:=#nº°]\\s*)?\\d+\\b";

    /** O par inteiro entre parenteses: "(id 14273)", "(#14273)". */
    private static final Pattern ENTRE_PARENTESES = Pattern.compile(
            "\\s*\\(\\s*(?:" + ROTULO_E_NUMERO + "|#\\d{2,})\\s*\\)", OPCOES);

    /** Solto na frase: "id 14273", "idObrigacao: 14273", "#14273". */
    private static final Pattern SOLTO = Pattern.compile(
            "\\s*(?:" + ROTULO_E_NUMERO + "|(?<![\\w&])#\\d{2,}\\b)", OPCOES);

    /** O espaco que sobra antes da pontuacao quando o id estava no fim da oracao. */
    private static final Pattern ESPACO_ANTES_DE_PONTUACAO = Pattern.compile("[ \\t]+([,.;:!?)])");

    /**
     * A resposta sem ids internos.
     *
     * @return o mesmo texto quando nao havia nada a remover
     */
    public String limpar(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return resposta;
        }
        String limpa = ENTRE_PARENTESES.matcher(resposta).replaceAll("");
        limpa = SOLTO.matcher(limpa).replaceAll("");
        if (limpa.equals(resposta)) {
            return resposta;
        }
        return ESPACO_ANTES_DE_PONTUACAO.matcher(limpa).replaceAll("$1").strip();
    }
}
