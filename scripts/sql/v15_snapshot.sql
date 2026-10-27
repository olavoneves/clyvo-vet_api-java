-- ============================================================
-- CLYVO VET — Snapshot de equivalência da V15
--
-- Fotografa o que a V15 não pode mudar: os dois painéis, os volumes do motor e
-- a integridade da cadeia de auditoria. Roda igual antes e depois da V15 — a
-- verificação de cadeia chama FN_CLV_VERIFICA_CADEIA se ela ainda existir, e
-- PKG_CLV_AUDITORIA.VERIFICA_CADEIA depois que a V15 a apagou. Por isso a
-- saída não diz qual das duas rodou: o diff entre as duas fotos tem que ser
-- vazio.
--
-- Nada aqui escreve no banco: só SELECT, ALTER SESSION de formato e uma
-- função de verificação que só lê. Sem DML, sem DDL, sem COMMIT.
--
-- No SQL Developer: abrir este arquivo, escolher a conexão e rodar com F5
-- (Run Script); a saída sai na aba Script Output. Comandos SET que o SQL
-- Developer não reconhece são pulados com aviso, igual nas duas fotos. Não
-- termina com EXIT de propósito: no F5 ele encerraria a sessão da planilha.
--
-- No SQL*Plus, no XE do docker-compose:
--   docker exec -i -e NLS_LANG=.AL32UTF8 oracle-clyvovet \
--     sqlplus -s -L petflow/<senha>@localhost:1521/PETFLOWDB \
--     < scripts/sql/v15_snapshot.sql > docs/v15-snapshot-antes.txt
--   (aplica a V15)
--   ... > docs/v15-snapshot-depois.txt
--   diff docs/v15-snapshot-antes.txt docs/v15-snapshot-depois.txt
--
-- Os .txt não são commitados; este .sql é.
-- ============================================================

WHENEVER SQLERROR EXIT FAILURE ROLLBACK
SET FEEDBACK OFF
SET TAB OFF
SET TRIMSPOOL ON
SET TRIMOUT ON
SET LINESIZE 400
SET PAGESIZE 50000
SET VERIFY OFF
SET SERVEROUTPUT ON SIZE UNLIMITED FORMAT WRAPPED
ALTER SESSION SET NLS_NUMERIC_CHARACTERS = '.,';
ALTER SESSION SET NLS_DATE_FORMAT = 'YYYY-MM-DD';

PROMPT === VW_CLV_PAINEL_COORTE
SELECT *
  FROM VW_CLV_PAINEL_COORTE
 ORDER BY id_clinica, ds_grupo;

PROMPT === VW_CLV_PAINEL_RECEITA
SELECT *
  FROM VW_CLV_PAINEL_RECEITA
 ORDER BY id_clinica, mes_referencia, fl_grupo_controle;

PROMPT === Volumes
SELECT 'TB_CLV_OBRIGACAO' AS tabela, COUNT(*) AS linhas FROM TB_CLV_OBRIGACAO
UNION ALL
SELECT 'TB_CLV_AUDIT_OBRIGACAO', COUNT(*) FROM TB_CLV_AUDIT_OBRIGACAO
UNION ALL
SELECT 'TB_CLV_OBRIGACAO_TRANSICAO', COUNT(*) FROM TB_CLV_OBRIGACAO_TRANSICAO;

PROMPT === Ponta de cada cadeia (soma de verificacao dos hashes)
-- Uma impressão digital da tabela de auditoria inteira: se algum elo mudasse
-- de conteúdo ou de ordem, a soma mudaria.
SELECT COUNT(DISTINCT id_obrigacao)                        AS obrigacoes_com_cadeia,
       MAX(nr_seq)                                         AS maior_seq,
       TO_CHAR(SUM(ORA_HASH(id_obrigacao || '|' || nr_seq || '|' || ds_hash_atual)),
               'FM999999999999999999999')                  AS soma_hashes
  FROM TB_CLV_AUDIT_OBRIGACAO;

PROMPT === Cadeia de auditoria
DECLARE
    v_rotina    VARCHAR2(60);
    v_resultado VARCHAR2(400);
    v_integras  NUMBER := 0;
    v_sem_reg   NUMBER := 0;
    v_quebradas NUMBER := 0;
    v_elos      NUMBER := 0;
    v_total     NUMBER := 0;

    FUNCTION verificar (p_id IN NUMBER) RETURN VARCHAR2 IS
        v_r VARCHAR2(400);
    BEGIN
        EXECUTE IMMEDIATE 'BEGIN :r := ' || v_rotina || '(:id); END;'
            USING OUT v_r, IN p_id;
        RETURN v_r;
    END;
BEGIN
    SELECT CASE
             WHEN EXISTS (SELECT 1 FROM user_objects
                           WHERE object_name = 'PKG_CLV_AUDITORIA'
                             AND object_type = 'PACKAGE BODY')
             THEN 'PKG_CLV_AUDITORIA.VERIFICA_CADEIA'
             ELSE 'FN_CLV_VERIFICA_CADEIA'
           END
      INTO v_rotina
      FROM dual;

    -- a base inteira, numa passada
    DBMS_OUTPUT.PUT_LINE('todas as obrigacoes: ' || verificar(NULL));

    -- e uma por uma, para que uma quebra apareça com o id dela
    FOR o IN (SELECT id_obrigacao FROM TB_CLV_OBRIGACAO ORDER BY id_obrigacao) LOOP
        v_total     := v_total + 1;
        v_resultado := verificar(o.id_obrigacao);
        IF v_resultado LIKE 'INTEGRA (%' THEN
            v_integras := v_integras + 1;
            v_elos     := v_elos + TO_NUMBER(REGEXP_SUBSTR(v_resultado, '[0-9]+'));
        ELSIF v_resultado = 'SEM REGISTROS' THEN
            v_sem_reg := v_sem_reg + 1;
        ELSE
            v_quebradas := v_quebradas + 1;
            DBMS_OUTPUT.PUT_LINE('  ' || o.id_obrigacao || ': ' || v_resultado);
        END IF;
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('obrigacoes verificadas uma a uma: ' || v_total);
    DBMS_OUTPUT.PUT_LINE('  integras:      ' || v_integras || ' (' || v_elos || ' elos)');
    DBMS_OUTPUT.PUT_LINE('  sem registros: ' || v_sem_reg);
    DBMS_OUTPUT.PUT_LINE('  quebradas:     ' || v_quebradas);
END;
/

PROMPT === USER_DEPENDENCIES das views
COLUMN name            FORMAT A24
COLUMN referenced_type FORMAT A16
COLUMN referenced_name FORMAT A32
-- Antes e depois tem que dar igual: as views leem só tabelas, e a V15 não
-- pode ter criado dependência de view para pacote.
SELECT name, referenced_type, referenced_name
  FROM user_dependencies
 WHERE name IN ('VW_CLV_PAINEL_COORTE', 'VW_CLV_PAINEL_RECEITA')
   AND referenced_owner = USER
 ORDER BY name, referenced_type, referenced_name;
