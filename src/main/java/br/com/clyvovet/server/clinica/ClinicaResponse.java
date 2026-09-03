package br.com.clyvovet.server.clinica;

import java.math.BigDecimal;

public record ClinicaResponse(
        Long id, String nome, String cnpj, String logradouro, String numero,
        String bairro, String cidade, String estado, String cep, String telefone,
        BigDecimal nrTicketMedio
) {
    public static ClinicaResponse from(Clinica c) {
        return new ClinicaResponse(
                c.getId(), c.getNome(), c.getCnpj(), c.getLogradouro(), c.getNumero(),
                c.getBairro(), c.getCidade(), c.getEstado(), c.getCep(), c.getTelefone(),
                c.getNrTicketMedio());
    }
}
