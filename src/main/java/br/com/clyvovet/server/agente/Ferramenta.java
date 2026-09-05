package br.com.clyvovet.server.agente;

import java.util.Map;

/**
 * Uma capacidade que o agente pode acionar.
 *
 * <p>Cada implementacao e um metodo Java sobre servicos que ja existiam antes do
 * agente. O modelo escolhe qual chamar e com quais argumentos; quem decide o que
 * isso significa no banco continua sendo o codigo de dominio, com as mesmas
 * validacoes que valem para a tela do veterinario.
 *
 * <p>Nenhuma ferramenta recebe o tenant pelo esquema. A clinica vem do
 * {@link ContextoDoAgente}, que por sua vez vem do TenantContext: um parametro
 * de clinica exposto ao modelo seria um id de clinica que o texto do tutor pode
 * influenciar.
 */
public interface Ferramenta {

    /** Nome que o modelo usa para chamar. Estavel: muda-lo e mudar o contrato. */
    String nome();

    /**
     * O que a ferramenta faz, para o modelo decidir quando usa-la.
     *
     * <p>E prompt, nao documentacao: e a unica coisa que o modelo le antes de
     * escolher. Descricao vaga produz ferramenta chamada na hora errada.
     */
    String descricao();

    /** Esquema JSON dos argumentos. Ver {@link Esquemas}. */
    Map<String, Object> esquema();

    /**
     * Executa a ferramenta.
     *
     * <p>Pode lancar excecao de dominio — conflito de horario, entidade
     * inexistente. Quem chama devolve a mensagem ao modelo como resultado da
     * ferramenta, para que ele contorne conversando, em vez de derrubar o turno.
     */
    Resultado executar(Map<String, Object> entrada, ContextoDoAgente contexto);

    /**
     * O que a ferramenta devolve.
     *
     * @param conteudo      serializado como JSON e entregue ao modelo
     * @param acao          mudanca de estado a reportar ao cliente, ou nulo
     * @param escalonamento verdadeiro quando a conversa passa a ser humana
     */
    record Resultado(Object conteudo, AcaoDoAgente acao, boolean escalonamento) {

        public static Resultado leitura(Object conteudo) {
            return new Resultado(conteudo, null, false);
        }

        public static Resultado escrita(Object conteudo, AcaoDoAgente acao) {
            return new Resultado(conteudo, acao, false);
        }

        public static Resultado escalonamento(Object conteudo, AcaoDoAgente acao) {
            return new Resultado(conteudo, acao, true);
        }
    }
}
