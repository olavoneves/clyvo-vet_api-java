package br.com.clyvovet.server.agente;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Montagem dos esquemas JSON que declaram os argumentos de cada ferramenta.
 *
 * <p>Existe para que o esquema fique legivel na propria ferramenta, em duas ou
 * tres linhas, em vez de trinta linhas de mapas aninhados. E para que
 * {@code additionalProperties: false} e a lista de obrigatorios — que a API
 * exige para validar a chamada — nao dependam de alguem lembrar de escrever.
 *
 * <p>Todo campo declarado aqui e obrigatorio. Argumento opcional em ferramenta
 * de agente e fonte de ambiguidade: o modelo omite, o codigo assume um padrao, e
 * o tutor recebe um horario que ninguem pediu. Quando um valor pode faltar, o
 * lugar de resolver isso e a descricao — pedindo que o modelo pergunte.
 */
public final class Esquemas {

    private Esquemas() {
    }

    /** Um campo do esquema: nome, tipo JSON e para que serve. */
    public record Campo(String nome, String tipo, String descricao) {
    }

    public static Campo inteiro(String nome, String descricao) {
        return new Campo(nome, "integer", descricao);
    }

    public static Campo texto(String nome, String descricao) {
        return new Campo(nome, "string", descricao);
    }

    /** Objeto fechado: nenhuma propriedade alem das declaradas, todas exigidas. */
    public static Map<String, Object> objeto(Campo... campos) {
        Map<String, Object> propriedades = new LinkedHashMap<>();
        List<String> obrigatorios = new ArrayList<>();

        for (Campo campo : campos) {
            propriedades.put(campo.nome(), Map.of(
                    "type", campo.tipo(),
                    "description", campo.descricao()));
            obrigatorios.add(campo.nome());
        }

        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "object");
        esquema.put("properties", propriedades);
        esquema.put("required", obrigatorios);
        esquema.put("additionalProperties", false);
        return esquema;
    }

    /**
     * Le um campo inteiro do que o modelo mandou.
     *
     * <p>O JSON chega com numero inteiro como {@code Integer} ou {@code Long}
     * conforme a magnitude, e ids do Oracle sao NUMBER(19). Converter pelo
     * {@link Number} evita o {@code ClassCastException} que so apareceria quando
     * a base crescesse.
     */
    public static Long inteiroDe(Map<String, Object> entrada, String campo) {
        Object valor = entrada == null ? null : entrada.get(campo);
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        if (valor instanceof String texto && !texto.isBlank()) {
            try {
                return Long.valueOf(texto.trim());
            } catch (NumberFormatException ignorado) {
                throw new IllegalArgumentException("O campo " + campo + " não é um número: " + texto);
            }
        }
        throw new IllegalArgumentException("O campo " + campo + " é obrigatório");
    }

    public static String textoDe(Map<String, Object> entrada, String campo) {
        Object valor = entrada == null ? null : entrada.get(campo);
        if (valor == null || valor.toString().isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório");
        }
        return valor.toString().trim();
    }
}
