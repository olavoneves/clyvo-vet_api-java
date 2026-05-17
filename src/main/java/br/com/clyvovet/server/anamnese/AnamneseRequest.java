package br.com.clyvovet.server.anamnese;

import br.com.clyvovet.server.enums.CondicaoCorporal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AnamneseRequest(
        @NotNull Long consultaId,
        @NotBlank @Size(max = 500) String queixaPrincipal,
        @Size(max = 2000) String historicoRelatado,
        @NotNull @DecimalMin("0.0") BigDecimal pesoKg,
        BigDecimal temperaturaC,
        Integer freqCardiaca,
        Integer freqRespiratoria,
        CondicaoCorporal condicaoCorporal,
        @Size(max = 100) String mucosas,
        @Size(max = 100) String linfonodos,
        @Size(max = 2000) String observacoesClinicas
) {}
