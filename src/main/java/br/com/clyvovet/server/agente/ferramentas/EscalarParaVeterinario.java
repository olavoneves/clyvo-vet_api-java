package br.com.clyvovet.server.agente.ferramentas;

import br.com.clyvovet.server.agente.AcaoDoAgente;
import br.com.clyvovet.server.agente.ContextoDoAgente;
import br.com.clyvovet.server.agente.Esquemas;
import br.com.clyvovet.server.agente.Ferramenta;
import br.com.clyvovet.server.agente.MensagensDoAgente;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Passa a conversa para uma pessoa.
 *
 * <p>A saida honesta do agente. Existe para que "nao sei" e "isso nao e comigo"
 * tenham um caminho que termina em alguem respondendo, em vez de terminar em um
 * palpite bem escrito.
 *
 * <p>Nao escreve estado por conta propria: devolve o sinal de escalonamento e
 * quem marca a conversa e persiste o evento e o laco do agente, que e onde o
 * turno inteiro — e nao so esta chamada — fica registrado.
 */
@Slf4j
@Component
public class EscalarParaVeterinario implements Ferramenta {

    @Override
    public String nome() {
        return "escalar_para_veterinario";
    }

    @Override
    public String descricao() {
        return """
                Encaminha a conversa para um veterinário humano e encerra sua participação \
                no assunto. \
                Use SEMPRE que o tutor perguntar qualquer coisa sobre a saúde do animal: \
                sintoma, comportamento estranho, resultado de exame, remédio, dose, se algo \
                é normal, se é grave, o que fazer. Use também quando o tutor insistir depois \
                de você já ter explicado que não fala sobre saúde, quando ele estiver \
                claramente aflito, ou quando o pedido dele não couber em nenhuma das outras \
                ferramentas. \
                Escalar é a resposta certa com frequência. Não tente contornar.""";
    }

    @Override
    public Map<String, Object> esquema() {
        return Esquemas.objeto(
                Esquemas.inteiro("idPet", "Id do pet da conversa."),
                Esquemas.texto("resumo",
                        "Resumo objetivo, em uma ou duas frases, do que o tutor pediu e por que "
                                + "isso precisa de um veterinário. É o que a equipe clínica vai ler."));
    }

    @Override
    public Resultado executar(Map<String, Object> entrada, ContextoDoAgente contexto) {
        FerramentaGuardas.exigirPetDaConversa(Esquemas.inteiroDe(entrada, "idPet"), contexto);
        String resumo = Esquemas.textoDe(entrada, "resumo");

        log.info("Conversa do agente escalada: clinica={} pet={} tutor={} motivo={}",
                contexto.idClinica(), contexto.idPet(), contexto.idTutor(), resumo);

        AcaoDoAgente acao = new AcaoDoAgente(AcaoDoAgente.ESCALONAMENTO, contexto.idPet(),
                "Conversa encaminhada para a equipe clínica");

        return Resultado.escalonamento(Map.of(
                "escalado", true,
                "resumo", resumo,
                "instrucao", "Responda ao tutor confirmando o encaminhamento, sem opinar sobre "
                        + "a saúde do animal. Sugestão de texto: " + MensagensDoAgente.ESCALONAMENTO),
                acao);
    }
}
