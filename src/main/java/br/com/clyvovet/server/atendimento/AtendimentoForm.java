package br.com.clyvovet.server.atendimento;

import br.com.clyvovet.server.enums.EventoGatilho;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O formulario de "Registrar atendimento".
 *
 * <p>Junta os campos de {@code ConsultaRequest} e de {@code GerarObrigacoesRequest}
 * com as mesmas restricoes, so com a mensagem em portugues para a tela. O pet
 * nao esta aqui: vem da rota, como o pet da obrigacao vem da consulta na API.
 *
 * <p>Classe com getter e setter, e nao record: o {@code th:field} do Thymeleaf
 * le e escreve pelas propriedades do bean.
 */
@Getter
@Setter
public class AtendimentoForm {

    @NotNull(message = "Escolha o veterinário")
    private Long veterinarioId;

    @NotNull(message = "Informe a data do atendimento")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dtConsulta;

    @Size(max = 500, message = "No máximo 500 caracteres")
    private String motivo;

    @Size(max = 1000, message = "No máximo 1000 caracteres")
    private String diagnostico;

    @PositiveOrZero(message = "O valor não pode ser negativo")
    private BigDecimal nrValor;

    @NotNull(message = "Escolha o gatilho")
    private EventoGatilho eventoGatilho;

    @Size(max = 200, message = "No máximo 200 caracteres")
    private String contexto;
}
