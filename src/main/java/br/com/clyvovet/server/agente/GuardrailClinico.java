package br.com.clyvovet.server.agente;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Segunda camada do guardrail clinico: le a resposta pronta antes de o tutor ver.
 *
 * <p>A primeira camada e a instrucao no prompt do sistema. Esta existe porque
 * instrucao em prompt nao e controle: e um pedido muito bem colocado a um
 * sistema que erra. Se alguem na banca perguntar por que confiar no agente, a
 * resposta e que nao confiamos — verificamos na saida, em codigo, com o
 * resultado registrado.
 *
 * <p>O que ela e: um detector de padroes de conteudo clinico. O que ela nao e:
 * um classificador. Ela erra para o lado de escalar — barrar uma frase inocente
 * custa um encaminhamento a mais para a equipe; deixar passar uma dosagem custa
 * um animal.
 *
 * <p>Nenhum padrao daqui bate em conversa de agenda. Foram escritos contra o
 * vocabulario do dominio: "vacina", "vermifugação", "consulta" e "retorno"
 * aparecem o tempo todo em texto legitimo e nunca disparam sozinhos — o que
 * dispara e o verbo de conduta ao lado deles.
 */
@Component
public class GuardrailClinico {

    /**
     * UNICODE_CHARACTER_CLASS nao e detalhe: sem ele a borda de palavra e a
     * classe de palavra so enxergam ASCII, e letra acentuada deixa de contar.
     * "é normal" e "provavelmente é" passariam batido — justamente as frases mais
     * comuns de tranquilizacao clinica em portugues.
     */
    private static final int OPCOES = Pattern.CASE_INSENSITIVE
            | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    /** Um padrao proibido e o nome do que ele detecta, para o registro. */
    private record Padrao(String categoria, Pattern expressao) {

        static Padrao de(String categoria, String expressao) {
            return new Padrao(categoria, Pattern.compile(expressao, OPCOES));
        }
    }

    private static final List<Padrao> PROIBIDOS = List.of(

            // Quantidade de medicamento. O padrao mais perigoso e o mais facil
            // de detectar: unidade de massa ou volume colada a um numero.
            Padrao.de("dosagem",
                    "\\b\\d+([.,]\\d+)?\\s*(mg|ml|mcg|ui)\\b|\\bmg\\s*/\\s*kg\\b"
                            + "|\\b\\d+\\s*(comprimidos?|c[áa]psulas?|gotas?|doses?)\\b"),

            // Ato de prescrever. "receita" fora daqui de proposito: no dominio
            // deste sistema ela e receita financeira, do painel.
            Padrao.de("prescricao",
                    "\\badministr(e|ar|ando)\\b|\\bministr(e|ar)\\b|\\bprescrev|\\bprescri[çc][ãa]o"
                            + "|\\bposologia\\b|\\bdosagem\\b|\\breceit(o|ar|ei)\\b"),

            // Nomear a doenca, ou apostar nela.
            Padrao.de("diagnostico",
                    "\\bdiagn[óo]stic|\\bsuspeita de\\b|\\btrata-se de\\b"
                            + "|\\bprovavelmente (é|e|est[áa]|seja|tem)\\b"
                            + "|\\bdeve ser (um|uma)\\b|\\bparece ser (um|uma)\\b"),

            // Interpretar o que o tutor descreveu.
            Padrao.de("interpretacao-de-sintoma",
                    "\\b(esse|este|isso|isto)\\s+(sintoma|quadro|sinal)\\b"
                            + "|\\b(indica|sugere|significa)\\s+(que\\s+)?(uma?\\s+)?"
                            + "(infec[çc]|doen[çc]|problema|quadro|alergi|verminose)"
                            + "|\\b[ée] sinal de\\b|\\b[ée] normal\\b|\\bn[ãa]o [ée] (grave|nada)\\b"
                            + "|\\b[ée] grave\\b"),

            // Mandar fazer alguma coisa com o animal.
            Padrao.de("conduta",
                    "\\b(trate|tratar)\\s+com\\b"
                            + "|\\b(d[êe]|dar|ofere[çc]a|aplique|aplicar|use|usar)\\s+"
                            + "(o\\s+|a\\s+|um\\s+|uma\\s+)?"
                            + "(rem[ée]dio|medicament|antibi[óo]tic|anti-inflamat|verm[íi]fug|antipulg)"),

            // Desaconselhar cuidado e o oposto do que este produto existe para fazer.
            Padrao.de("desaconselhar-cuidado",
                    "\\bn[ãa]o precisa (levar|ir ao|de consulta|se preocupar)\\b"
                            + "|\\bn[ãa]o [ée] necess[áa]rio (levar|consulta|ir)\\b"));

    /**
     * A categoria do primeiro padrao que bateu, se algum bateu.
     *
     * @return vazio quando a resposta pode ir para o tutor como esta
     */
    public Optional<String> conteudoClinico(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return Optional.empty();
        }
        return PROIBIDOS.stream()
                .filter(padrao -> padrao.expressao().matcher(resposta).find())
                .map(Padrao::categoria)
                .findFirst();
    }
}
