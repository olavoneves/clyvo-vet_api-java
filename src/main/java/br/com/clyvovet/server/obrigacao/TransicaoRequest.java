package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.enums.ObrigacaoStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Pedido de transicao. Quem valida se o salto e legal e
 * PR_CLV_TRANSITAR_OBRIGACAO, nao esta classe.
 */
public record TransicaoRequest(
        @NotNull ObrigacaoStatus novoStatus,
        @Size(max = 300) String motivo,
        // preenchidos conforme o destino: agendamento em AGENDADA, consulta e
        // valor em CUMPRIDA
        Long agendamentoId,
        Long consultaId,
        @PositiveOrZero BigDecimal valor
) {}
