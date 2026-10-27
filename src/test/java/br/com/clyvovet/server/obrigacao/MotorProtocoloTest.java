package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.enums.EventoGatilho;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.StoredProcedureQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * O adaptador do motor, sem banco: o que ele entrega a procedure.
 *
 * <p>Um NULL explicito anula o DEFAULT de um parametro PL/SQL. Com
 * P_HORIZONTE_DIAS nulo o limite do laco de recorrencia vira NULL, a
 * comparacao de saida nunca e verdadeira e a procedure soma datas ate estourar
 * o ano 9999 (ORA-01841) - foi o 500 que o POST /consultas/{id}/obrigacoes
 * devolvia sem "horizonteDias" no corpo. O teste de integracao nao pegava
 * porque sempre informa o horizonte.
 */
class MotorProtocoloTest {

    private EntityManager entityManager;
    private StoredProcedureQuery procedure;
    private MotorProtocolo motor;

    @BeforeEach
    void montar() {
        entityManager = mock(EntityManager.class);
        procedure = mock(StoredProcedureQuery.class);
        given(entityManager.createStoredProcedureQuery(anyString())).willReturn(procedure);
        given(procedure.getOutputParameterValue("O_QTD_CRIADAS")).willReturn(3);
        motor = new MotorProtocolo(entityManager);
    }

    @Test
    @DisplayName("sem horizonte informado, envia o horizonte padrao do motor e nunca NULL")
    void gerarObrigacoes_semHorizonte_enviaHorizontePadrao() {
        motor.gerarObrigacoes(1L, 2L, 3L, EventoGatilho.CONSULTA_REGISTRADA,
                "corr", LocalDate.of(2026, 10, 6), null, null);

        verify(procedure).setParameter("P_HORIZONTE_DIAS", MotorProtocolo.HORIZONTE_PADRAO_DIAS);
    }

    @Test
    @DisplayName("com horizonte informado, envia o valor recebido")
    void gerarObrigacoes_comHorizonte_enviaOValorRecebido() {
        motor.gerarObrigacoes(1L, 2L, 3L, EventoGatilho.CONSULTA_REGISTRADA,
                "corr", LocalDate.of(2026, 10, 6), null, 90);

        verify(procedure).setParameter("P_HORIZONTE_DIAS", 90);
    }

    /*
     * Desde a V15 as rotinas moram em PKG_CLV_MOTOR e as soltas da V7 nao
     * existem mais. O stub acima aceita qualquer nome, entao sem estes dois
     * testes um nome velho so apareceria como PLS-00201 no banco.
     */

    @Test
    @DisplayName("a geracao chama a procedure pelo nome qualificado do pacote")
    void gerarObrigacoes_chamaOPacoteDoMotor() {
        motor.gerarObrigacoes(1L, 2L, 3L, EventoGatilho.CONSULTA_REGISTRADA,
                "corr", LocalDate.of(2026, 10, 6), null, null);

        verify(entityManager).createStoredProcedureQuery("PKG_CLV_MOTOR.GERAR_OBRIGACOES");
    }

    @Test
    @DisplayName("a transicao chama a procedure pelo nome qualificado do pacote")
    void transitar_chamaOPacoteDoMotor() {
        motor.transitar(10L, ObrigacaoStatus.CUMPRIDA, "teste", "caus", null, null, null);

        verify(entityManager).createStoredProcedureQuery("PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO");
    }
}
