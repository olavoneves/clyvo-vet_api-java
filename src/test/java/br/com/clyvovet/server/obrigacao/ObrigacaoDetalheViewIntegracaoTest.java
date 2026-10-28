package br.com.clyvovet.server.obrigacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VW_CLV_OBRIGACAO_DETALHE contra o banco.
 *
 * <p>Nenhum codigo Java le esta view: quem a consome e o clyvo-insights (.NET),
 * escrito em outro repositorio contra o contrato de colunas da V16. Este teste
 * e o unico lugar deste lado que quebra se alguem renomear, reordenar ou
 * acrescentar uma coluna — mudanca que o .NET so descobriria em producao.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle configurado nas variaveis de ambiente")
class ObrigacaoDetalheViewIntegracaoTest {

    /** O contrato combinado com o .NET, na ordem: nome, tipo, escala. */
    private static final List<String> CONTRATO = List.of(
            "ID_OBRIGACAO NUMBER 0",
            "ID_CLINICA NUMBER 0",
            "ID_PET NUMBER 0",
            "NM_PET VARCHAR2 -",
            "NM_TUTOR VARCHAR2 -",
            "NM_PROTOCOLO VARCHAR2 -",
            "DS_CATEGORIA VARCHAR2 -",
            "NM_ETAPA VARCHAR2 -",
            "DS_STATUS VARCHAR2 -",
            "FL_GRUPO_CONTROLE CHAR -",
            "DT_PREVISTA DATE -",
            "DT_JANELA_INICIO DATE -",
            "DT_JANELA_FIM DATE -",
            "NR_VALOR_ESTIMADO NUMBER 2",
            "NR_VALOR_REALIZADO NUMBER 2");

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("as colunas sao exatamente as do contrato com o .NET, na ordem")
    void colunasDoContrato() {
        List<String> colunas = jdbc.sql("""
                        select column_name || ' ' || data_type || ' ' || nvl(to_char(data_scale), '-')
                          from user_tab_columns
                         where table_name = 'VW_CLV_OBRIGACAO_DETALHE'
                         order by column_id
                        """)
                .query(String.class)
                .list();

        assertThat(colunas).containsExactlyElementsOf(CONTRATO);
    }

    @Test
    @DisplayName("a view ve todas as obrigacoes: nenhum inner join descarta linha")
    void umaLinhaPorObrigacao() {
        Long naTabela = jdbc.sql("select count(*) from TB_CLV_OBRIGACAO").query(Long.class).single();
        Long naView = jdbc.sql("select count(*) from VW_CLV_OBRIGACAO_DETALHE").query(Long.class).single();

        assertThat(naView).isEqualTo(naTabela);
    }

    @Test
    @DisplayName("a view le so tabelas, nunca o pacote do motor")
    void dependeSoDeTabelas() {
        List<String> tipos = jdbc.sql("""
                        select distinct referenced_type
                          from user_dependencies
                         where name = 'VW_CLV_OBRIGACAO_DETALHE'
                           and referenced_owner = user
                        """)
                .query(String.class)
                .list();

        assertThat(tipos).containsExactly("TABLE");
    }
}
