package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.enums.ObrigacaoStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/obrigacoes")
@RequiredArgsConstructor
@Tag(name = "Obrigações", description = "Compromissos de cuidado gerados pelo motor de protocolo")
public class ObrigacaoController {

    private final ObrigacaoService service;

    @GetMapping
    @Operation(summary = "Listar obrigações com filtros opcionais de status, janela e pet")
    public Page<ObrigacaoResponse> buscar(
            @RequestParam(required = false) ObrigacaoStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long petId,
            Pageable pageable) {
        return service.buscar(status, de, ate, petId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obrigação com a linha do tempo de transições")
    @ApiResponse(responseCode = "404", description = "Não encontrada")
    public ObrigacaoDetalheResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/transicoes")
    @Operation(summary = "Transitar status da obrigação (validado pelo motor no banco)")
    @ApiResponse(responseCode = "409", description = "Transição inválida para o estado atual")
    public ObrigacaoDetalheResponse transitar(@PathVariable Long id,
                                              @Valid @RequestBody TransicaoRequest request) {
        return service.transitar(id, request);
    }
}
