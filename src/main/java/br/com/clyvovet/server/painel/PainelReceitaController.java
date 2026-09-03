package br.com.clyvovet.server.painel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/painel")
@RequiredArgsConstructor
@Tag(name = "Painel", description = "Indicadores de receita recuperada")
public class PainelReceitaController {

    private final PainelReceitaService service;

    @GetMapping("/receita")
    @Operation(summary = "Funil, receita recuperada e comparação tratado vs. controle do mês")
    public PainelReceitaResponse receita(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mes) {
        return service.doMes(mes != null ? mes : LocalDate.now());
    }
}
