package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.consulta.Consulta;
import br.com.clyvovet.server.consulta.ConsultaRepository;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObrigacaoService {

    private final ObrigacaoRepository repository;
    private final ObrigacaoTransicaoRepository transicaoRepository;
    private final ConsultaRepository consultaRepository;
    private final MotorProtocolo motor;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<ObrigacaoResponse> buscar(ObrigacaoStatus status, LocalDate de, LocalDate ate,
                                          Long petId, Pageable pageable) {
        return repository.findAll(ObrigacaoSpecs.filtro(status, de, ate, petId), pageable)
                .map(ObrigacaoResponse::from);
    }

    @Transactional(readOnly = true)
    public ObrigacaoDetalheResponse findById(Long id) {
        Obrigacao obrigacao = buscarEntidade(id);
        return ObrigacaoDetalheResponse.from(
                obrigacao, transicaoRepository.findByObrigacaoIdOrderByDtOcorrenciaAsc(id));
    }

    /**
     * Delega a transicao ao motor. Se o salto for ilegal, a procedure levanta
     * ORA-20010 e o pedido vira 409 sem tocar em nada.
     */
    @Transactional
    public ObrigacaoDetalheResponse transitar(Long id, TransicaoRequest request) {
        // valida existencia e tenant antes de chamar o motor: a procedure nao
        // conhece o TenantContext e aceitaria um id de outra clinica
        Obrigacao obrigacao = buscarEntidade(id);

        motor.transitar(id, request.novoStatus(), request.motivo(),
                obrigacao.getDsCorrelationId(), request.agendamentoId(),
                request.consultaId(), request.valor());

        // a procedure escreveu direto no banco: o que esta na sessao do Hibernate
        // envelheceu no mesmo instante
        entityManager.clear();

        return findById(id);
    }

    /**
     * Registra o gatilho de uma consulta e materializa as obrigacoes que ele
     * produz. O pet vem da consulta, nunca do corpo da requisicao.
     */
    @Transactional
    public GerarObrigacoesResponse gerarParaConsulta(Long consultaId, GerarObrigacoesRequest request) {
        Consulta consulta = consultaRepository.findById(consultaId)
                .orElseThrow(() -> new EntityNotFoundException("Consulta", consultaId));

        String correlationId = UUID.randomUUID().toString();
        LocalDate dataReferencia = request.dataReferencia() != null
                ? request.dataReferencia()
                : consulta.getDtConsulta();

        int criadas = motor.gerarObrigacoes(
                TenantContext.getOrThrow(),
                consulta.getPet().getId(),
                consultaId,
                request.eventoGatilho(),
                correlationId,
                dataReferencia,
                request.contexto(),
                request.horizonteDias());

        entityManager.clear();

        return new GerarObrigacoesResponse(criadas, correlationId);
    }

    private Obrigacao buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Obrigação", id));
    }
}
