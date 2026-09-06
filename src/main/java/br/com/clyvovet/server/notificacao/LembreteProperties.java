package br.com.clyvovet.server.notificacao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da varredura automatica de lembretes.
 *
 * <p>Note o que <b>nao</b> esta aqui: quantos dias antes do vencimento o
 * lembrete sai. Isso e regra clinica e vive no catalogo de protocolos, coluna
 * {@code nr_antecedencia_lembrete_dias}. O que fica aqui e so operacao — quando
 * a varredura roda e quanto ela pode fazer de uma vez.
 *
 * @param habilitado    desliga a varredura sem tirar o bean do contexto
 * @param cron          quando varrer, no formato do Spring (seis campos)
 * @param fuso          fuso do cron; sem ele o horario seria o da maquina
 * @param maxPorExecucao teto de obrigacoes por clinica por execucao
 */
@ConfigurationProperties(prefix = "app.lembretes")
public record LembreteProperties(
        boolean habilitado,
        String cron,
        String fuso,
        int maxPorExecucao
) {

    public LembreteProperties {
        cron = cron != null && !cron.isBlank() ? cron : "0 0 8 * * *";
        fuso = fuso != null && !fuso.isBlank() ? fuso : "America/Sao_Paulo";
        maxPorExecucao = maxPorExecucao > 0 ? maxPorExecucao : 200;
    }
}
