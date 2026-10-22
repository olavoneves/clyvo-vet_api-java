package br.com.clyvovet.server.notificacao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendador da varredura de lembretes.
 *
 * <p>{@code @EnableScheduling} vive aqui e nao na classe da aplicacao: quem
 * precisa de agendamento e este modulo, e um teste que queira o resto do
 * contexto sem job rodando por baixo desliga por
 * {@code app.lembretes.habilitado=false} sem mexer no arranque da aplicacao
 * inteira.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(LembreteProperties.class)
public class LembreteConfig {
}
