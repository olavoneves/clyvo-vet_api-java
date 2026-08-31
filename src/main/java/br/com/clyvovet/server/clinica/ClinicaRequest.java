package br.com.clyvovet.server.clinica;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ClinicaRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Pattern(regexp = "\\d{14}", message = "CNPJ deve ter 14 dígitos") String cnpj,
        @NotBlank @Size(max = 200) String logradouro,
        @Size(max = 10) String numero,
        @Size(max = 80) String bairro,
        @NotBlank @Size(max = 80) String cidade,
        @NotBlank @Size(min = 2, max = 2) String estado,
        @Pattern(regexp = "\\d{8}", message = "CEP deve ter 8 dígitos") String cep,
        @Size(max = 20) String telefone,
        // opcional no cadastro: clínica nova ainda não tem histórico para calcular
        @PositiveOrZero BigDecimal nrTicketMedio
) {}
