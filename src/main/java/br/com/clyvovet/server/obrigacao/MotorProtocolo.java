package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.enums.EventoGatilho;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.exception.ConflitoDeEstadoException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.StoredProcedureQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Date;

/**
 * Unico ponto da aplicacao que fala com o motor de protocolo em PL/SQL.
 *
 * <p>A logica de quando gerar obrigacao e de qual transicao e legal vive no
 * banco, junto com a auditoria encadeada e o outbox, tudo na mesma transacao.
 * Reimplementar isso em Java criaria duas fontes de verdade que divergiriam na
 * primeira regra nova.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MotorProtocolo {

    /** Faixa que o Oracle reserva para RAISE_APPLICATION_ERROR. */
    private static final int ORA_ERRO_APLICACAO_MIN = 20000;
    private static final int ORA_ERRO_APLICACAO_MAX = 20999;

    private final EntityManager entityManager;

    /**
     * Materializa as obrigacoes que o gatilho produz para o pet.
     *
     * @return quantas obrigacoes foram criadas
     */
    public int gerarObrigacoes(Long idClinica, Long idPet, Long idConsulta, EventoGatilho gatilho,
                               String correlationId, LocalDate dataReferencia,
                               String contexto, Integer horizonteDias) {
        StoredProcedureQuery procedure = entityManager
                .createStoredProcedureQuery("PR_CLV_GERAR_OBRIGACOES");

        registrarEntrada(procedure, "P_ID_CLINICA", Long.class);
        registrarEntrada(procedure, "P_ID_PET", Long.class);
        registrarEntrada(procedure, "P_ID_CONSULTA", Long.class);
        registrarEntrada(procedure, "P_EVENTO_GATILHO", String.class);
        registrarEntrada(procedure, "P_CORRELATION_ID", String.class);
        registrarEntrada(procedure, "P_DATA_REFERENCIA", Date.class);
        registrarEntrada(procedure, "P_CONTEXTO", String.class);
        registrarEntrada(procedure, "P_HORIZONTE_DIAS", Integer.class);
        procedure.registerStoredProcedureParameter("O_QTD_CRIADAS", Integer.class, ParameterMode.OUT);

        procedure.setParameter("P_ID_CLINICA", idClinica);
        procedure.setParameter("P_ID_PET", idPet);
        procedure.setParameter("P_ID_CONSULTA", idConsulta);
        procedure.setParameter("P_EVENTO_GATILHO", gatilho.name());
        procedure.setParameter("P_CORRELATION_ID", correlationId);
        procedure.setParameter("P_DATA_REFERENCIA", paraDate(dataReferencia));
        procedure.setParameter("P_CONTEXTO", contexto);
        procedure.setParameter("P_HORIZONTE_DIAS", horizonteDias);

        executar(procedure);

        Integer criadas = (Integer) procedure.getOutputParameterValue("O_QTD_CRIADAS");
        return criadas != null ? criadas : 0;
    }

    /** Aplica uma transicao de status. O motor recusa saltos ilegais. */
    public void transitar(Long idObrigacao, ObrigacaoStatus novoStatus, String motivo,
                          String causationId, Long idAgendamento, Long idConsulta, BigDecimal valor) {
        StoredProcedureQuery procedure = entityManager
                .createStoredProcedureQuery("PR_CLV_TRANSITAR_OBRIGACAO");

        registrarEntrada(procedure, "P_ID_OBRIGACAO", Long.class);
        registrarEntrada(procedure, "P_NOVO_STATUS", String.class);
        registrarEntrada(procedure, "P_MOTIVO", String.class);
        registrarEntrada(procedure, "P_USUARIO", String.class);
        registrarEntrada(procedure, "P_CAUSATION_ID", String.class);
        registrarEntrada(procedure, "P_ID_AGENDAMENTO", Long.class);
        registrarEntrada(procedure, "P_ID_CONSULTA", Long.class);
        registrarEntrada(procedure, "P_VALOR", BigDecimal.class);

        procedure.setParameter("P_ID_OBRIGACAO", idObrigacao);
        procedure.setParameter("P_NOVO_STATUS", novoStatus.name());
        procedure.setParameter("P_MOTIVO", motivo);
        procedure.setParameter("P_USUARIO", AuthenticatedUser.identificacaoAtual());
        procedure.setParameter("P_CAUSATION_ID", causationId);
        procedure.setParameter("P_ID_AGENDAMENTO", idAgendamento);
        procedure.setParameter("P_ID_CONSULTA", idConsulta);
        procedure.setParameter("P_VALOR", valor);

        executar(procedure);
    }

    private void registrarEntrada(StoredProcedureQuery procedure, String nome, Class<?> tipo) {
        procedure.registerStoredProcedureParameter(nome, tipo, ParameterMode.IN);
    }

    private Date paraDate(LocalDate data) {
        return data == null ? null : java.sql.Date.valueOf(data);
    }

    /**
     * Traduz a excecao de aplicacao do PL/SQL em erro de dominio. Sem isto, uma
     * transicao invalida chegaria ao cliente como 500 com stack trace de JDBC.
     */
    private void executar(StoredProcedureQuery procedure) {
        try {
            procedure.execute();
        } catch (PersistenceException ex) {
            throw erroDeAplicacao(ex)
                    .<RuntimeException>map(ConflitoDeEstadoException::new)
                    .orElse(ex);
        }
    }

    private java.util.Optional<String> erroDeAplicacao(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql
                    && sql.getErrorCode() >= ORA_ERRO_APLICACAO_MIN
                    && sql.getErrorCode() <= ORA_ERRO_APLICACAO_MAX) {
                return java.util.Optional.of(limpar(sql.getMessage()));
            }
        }
        return java.util.Optional.empty();
    }

    /** "ORA-20010: Transicao invalida: X -> Y" vira "Transicao invalida: X -> Y". */
    private String limpar(String mensagem) {
        String primeiraLinha = mensagem.lines().findFirst().orElse(mensagem).trim();
        int doisPontos = primeiraLinha.indexOf(": ");
        return doisPontos > 0 && primeiraLinha.startsWith("ORA-")
                ? primeiraLinha.substring(doisPontos + 2)
                : primeiraLinha;
    }
}
