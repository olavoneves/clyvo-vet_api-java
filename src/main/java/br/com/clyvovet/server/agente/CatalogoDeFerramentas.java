package br.com.clyvovet.server.agente;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * O que o agente sabe fazer, reunido a partir dos beans de {@link Ferramenta}.
 *
 * <p>Reunir por injecao e nao por lista escrita a mao: adicionar uma capacidade
 * passa a ser criar uma classe, e nao criar uma classe e lembrar de registra-la
 * em outro arquivo. O segundo passo e o que se esquece.
 *
 * <p>A ordem de declaracao e estavel — mapa que preserva insercao, sobre uma
 * lista ordenada por nome. Nao e detalhe: a lista de ferramentas e o inicio do
 * prompt, e prompt que muda de ordem a cada reinicio joga fora o cache da API.
 */
@Component
public class CatalogoDeFerramentas {

    private final Map<String, Ferramenta> porNome = new LinkedHashMap<>();
    private final List<ProtocoloAnthropic.Ferramenta> declaracoes;

    public CatalogoDeFerramentas(List<Ferramenta> ferramentas) {
        ferramentas.stream()
                .sorted((a, b) -> a.nome().compareTo(b.nome()))
                .forEach(f -> porNome.put(f.nome(), f));

        this.declaracoes = porNome.values().stream()
                .map(f -> new ProtocoloAnthropic.Ferramenta(
                        f.nome(), f.descricao(), f.esquema(), Boolean.TRUE))
                .toList();
    }

    /** As ferramentas como a API precisa recebe-las. */
    public List<ProtocoloAnthropic.Ferramenta> declaracoes() {
        return declaracoes;
    }

    /**
     * A ferramenta com este nome.
     *
     * <p>Nome desconhecido acontece: o modelo pode inventar um. Vira mensagem de
     * erro devolvida a ele, nao excecao — ele corrige na volta seguinte.
     */
    public Ferramenta porNome(String nome) {
        Ferramenta ferramenta = porNome.get(nome);
        if (ferramenta == null) {
            throw new IllegalArgumentException("Ferramenta desconhecida: " + nome);
        }
        return ferramenta;
    }
}
