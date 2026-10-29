package br.com.clyvovet.server.atendimento;

import br.com.clyvovet.server.consulta.ConsultaRequest;
import br.com.clyvovet.server.consulta.ConsultaResponse;
import br.com.clyvovet.server.consulta.ConsultaService;
import br.com.clyvovet.server.enums.ConsultaStatus;
import br.com.clyvovet.server.obrigacao.GerarObrigacoesRequest;
import br.com.clyvovet.server.obrigacao.GerarObrigacoesResponse;
import br.com.clyvovet.server.obrigacao.ObrigacaoResponse;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Registra um atendimento: cria a consulta e gera as obrigacoes do gatilho.
 *
 * <p>Sao as mesmas duas chamadas da API — {@code POST /api/consultas} e
 * {@code POST /api/consultas/{id}/obrigacoes} — e nenhuma regra nova. O que esta
 * classe acrescenta e a transacao unica. {@code PKG_CLV_MOTOR.GERAR_OBRIGACOES}
 * nao faz COMMIT (o unico COMMIT do motor e o do log de erro, em transacao
 * autonoma), entao quando o motor recusa o gatilho a consulta volta junto. Pela
 * API, a recusa deixa uma consulta sem obrigacao; pela tela, nao.
 *
 * <p>Mora num servico, e nao no controller, para que a fronteira da transacao
 * seja a do caso de uso e nao a da requisicao web.
 */
@Service
@RequiredArgsConstructor
public class RegistroDeAtendimentoService {

    /** Quantas datas a confirmacao mostra. As demais estao na ficha. */
    private static final Pageable PROXIMAS_DATAS =
            PageRequest.of(0, 3, Sort.by(Sort.Direction.ASC, "dtPrevista"));

    private final ConsultaService consultaService;
    private final ObrigacaoService obrigacaoService;

    @Transactional
    public AtendimentoRegistrado registrar(Long petId, AtendimentoForm form) {
        ConsultaResponse consulta = consultaService.create(new ConsultaRequest(
                form.getDtConsulta(),
                form.getMotivo(),
                form.getDiagnostico(),
                // quem registra pela tela registra o que ja aconteceu
                ConsultaStatus.REALIZADA,
                form.getNrValor(),
                petId,
                form.getVeterinarioId()));

        // dataReferencia e horizonte nulos: a ancora e a data da consulta e o
        // horizonte e o padrao do motor, como na API sem esses campos
        GerarObrigacoesResponse geradas = obrigacaoService.gerarParaConsulta(consulta.id(),
                new GerarObrigacoesRequest(form.getEventoGatilho(), null,
                        semBrancos(form.getContexto()), null));

        return new AtendimentoRegistrado(consulta.id(), geradas.qtdCriadas(),
                obrigacaoService.geradasPelaConsulta(consulta.id(), LocalDate.now(), PROXIMAS_DATAS)
                        .stream()
                        .map(ObrigacaoResponse::dtPrevista)
                        .toList());
    }

    /** Campo de texto vazio no formulario chega como "", e o motor espera nulo. */
    private static String semBrancos(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
