package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.enums.EventoGatilho;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GerarObrigacoesRequest(
        @NotNull EventoGatilho eventoGatilho,
        /** Ancora do calculo das datas. Nulo usa a data da consulta. */
        LocalDate dataReferencia,
        @Size(max = 200) String contexto,
        /** Ate quantos dias a frente materializar obrigacoes. Nulo usa o padrao do motor. */
        @Positive Integer horizonteDias
) {}
