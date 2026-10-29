-- ============================================================
-- CLYVO VET — Ensaio da demo de "Registrar atendimento"
--
-- COMO RODAR (SQL Developer):
--   1. Tools > Preferences > Database > Advanced: "Autocommit" DESLIGADO.
--   2. Abrir este arquivo, escolher a conexão e rodar com F5 (Run Script).
--      A saída do ensaio sai na aba Script Output (SERVEROUTPUT ligado abaixo).
--   3. Não rode nada antes na mesma aba: o script se recusa a começar com
--      transação aberta, para que o ROLLBACK final não leve trabalho seu junto.
--
-- O QUE FAZ. Para cada combinação gatilho + contexto do roteiro, escolhe pets
-- da clínica da Helena (achada pelo login dela, sem id fixo) cujos atributos
-- satisfazem uma regra de ativação do gatilho e cujo protocolo ainda não está
-- coberto. Chama PKG_CLV_MOTOR.GERAR_OBRIGACOES em cada um, conta o que foi
-- gerado e desfaz com ROLLBACK TO SAVEPOINT. Até 3 pets por combinação: o
-- primeiro que gerar é o do vídeo, os outros são reservas.
--
-- NADA FICA GRAVADO:
--   - o motor não faz COMMIT (V15, PKG_CLV_MOTOR.GERAR_OBRIGACOES);
--   - o trigger TR_CLV_AUDIT_OBRIGACAO grava a auditoria na mesma transação,
--     sem PRAGMA AUTONOMOUS_TRANSACTION: o rollback leva junto;
--   - a única transação autônoma do caminho é PKG_CLV_LOG.REGISTRAR_ERRO, que
--     só grava em TB_CLV_LOG_ERRO quando o motor falha. A contagem dela também
--     é conferida;
--   - sequências não voltam no rollback: os próximos ids de obrigação,
--     outbox e auditoria pulam alguns números. Não quebra nada: a cadeia de
--     auditoria encadeia por obrigação e não exige nr_seq contíguo;
--   - ROLLBACK final, e as contagens de antes e depois são comparadas.
--
-- Não termina com EXIT de propósito: no F5 ele encerraria a sessão da planilha.
-- ============================================================

SET SERVEROUTPUT ON SIZE UNLIMITED
SET AUTOCOMMIT OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

DECLARE
    -- O roteiro do vídeo. Os códigos de contexto são os que o select da tela
    -- oferece para "Procedimento realizado" (TB_CLV_CONDICAO_PROT).
    -- Dois arrays em paralelo, e não um array de records: o construtor de
    -- record só existe a partir do Oracle 18c.
    TYPE t_textos IS TABLE OF VARCHAR2(60);
    c_gatilhos  CONSTANT t_textos := t_textos(
        'PROCEDIMENTO_REALIZADO', 'PROCEDIMENTO_REALIZADO', 'FASE_VIDA_ALTERADA',
        'PESO_ATUALIZADO', 'CONSULTA_REGISTRADA');
    c_contextos CONSTANT t_textos := t_textos(
        'CIR_CASTRACAO', 'ODONTO_PROF', NULL, NULL, NULL);

    c_login_helena  CONSTANT VARCHAR2(80) := 'helena@vidaanimal.com.br';
    -- Thor e os outros pets da Camila ficam para o fluxo do agente
    c_tutor_agente  CONSTANT VARCHAR2(80) := 'camila.ferreira@exemplo.com';
    c_por_combo     CONSTANT PLS_INTEGER  := 3;
    c_marca         CONSTANT VARCHAR2(20) := 'ENSAIO-DEMO-';

    v_hoje      DATE := TRUNC(SYSDATE);
    v_clinica   NUMBER;
    v_qtd       NUMBER;
    v_achados   PLS_INTEGER;
    v_n         PLS_INTEGER := 0;
    v_protocolos VARCHAR2(400);
    v_primeira  DATE;

    TYPE t_usados IS TABLE OF BOOLEAN INDEX BY PLS_INTEGER;
    v_usados t_usados;

    v_obr_antes NUMBER; v_aud_antes NUMBER; v_out_antes NUMBER; v_log_antes NUMBER;
    v_obr_depois NUMBER; v_aud_depois NUMBER; v_out_depois NUMBER; v_log_depois NUMBER;
    v_marcadas  NUMBER;

    -- Espelho de PKG_CLV_MOTOR.AVALIA_CONDICAO, só para ESCOLHER candidatos.
    -- Quem decide de verdade é o motor, chamado logo depois; atributo que este
    -- espelho não conhece (CONDICAO, BRAQUICEFALICO) reprova o candidato.
    FUNCTION condicao_ok(p_cond NUMBER, p_pet NUMBER, p_contexto VARCHAR2) RETURN BOOLEAN IS
        c        TB_CLV_CONDICAO_PROT%ROWTYPE;
        v_min    NUMBER;
        v_max    NUMBER;
        v_porte  TB_CLV_PET.ds_porte%TYPE;
        v_t      VARCHAR2(400);
        v_num    NUMBER;
        v_numerico BOOLEAN := FALSE;
    BEGIN
        SELECT * INTO c FROM TB_CLV_CONDICAO_PROT WHERE id_condicao_prot = p_cond;
        SELECT ds_porte INTO v_porte FROM TB_CLV_PET WHERE id_pet = p_pet;
        v_min := c.nr_valor_min;
        v_max := c.nr_valor_max;

        IF c.fl_por_porte = 'S' THEN
            BEGIN
                SELECT nr_valor_min, nr_valor_max INTO v_min, v_max
                  FROM TB_CLV_PARAM_PORTE
                 WHERE id_condicao_prot = p_cond AND ds_porte = v_porte;
            EXCEPTION
                WHEN NO_DATA_FOUND THEN RETURN FALSE;
            END;
        END IF;

        CASE c.ds_atributo
            WHEN 'ESPECIE' THEN
                SELECT UPPER(e.nm_especie) INTO v_t
                  FROM TB_CLV_PET p
                  JOIN TB_CLV_RACA r    ON r.id_raca = p.id_raca
                  JOIN TB_CLV_ESPECIE e ON e.id_especie = r.id_especie
                 WHERE p.id_pet = p_pet;
                RETURN SUBSTR(v_t, 1, 3) = SUBSTR(UPPER(c.ds_valor_texto), 1, 3);
            WHEN 'PROCEDIMENTO' THEN
                RETURN p_contexto IS NOT NULL
                   AND INSTR(',' || UPPER(c.ds_valor_texto) || ',', ',' || UPPER(p_contexto) || ',') > 0;
            WHEN 'IDADE_MESES' THEN
                SELECT FLOOR(MONTHS_BETWEEN(v_hoje, dt_nascimento)) INTO v_num
                  FROM TB_CLV_PET WHERE id_pet = p_pet;
                v_numerico := TRUE;
            WHEN 'PESO_KG' THEN
                SELECT MAX(peso) KEEP (DENSE_RANK FIRST ORDER BY dt DESC) INTO v_num
                  FROM (SELECT a.nr_peso_kg peso, c2.dt_consulta dt
                          FROM TB_CLV_ANAMNESE a
                          JOIN TB_CLV_CONSULTA c2 ON c2.id_consulta = a.id_consulta
                         WHERE c2.id_pet = p_pet AND c2.dt_consulta <= v_hoje
                           AND a.nr_peso_kg IS NOT NULL);
                v_numerico := TRUE;
            WHEN 'PORTE' THEN
                v_t := v_porte;
            WHEN 'CASTRADO' THEN
                SELECT NVL(fl_castrado, 'N') INTO v_t FROM TB_CLV_PET WHERE id_pet = p_pet;
            WHEN 'SEXO' THEN
                SELECT ds_sexo INTO v_t FROM TB_CLV_PET WHERE id_pet = p_pet;
            WHEN 'RACA' THEN
                SELECT r.nm_raca INTO v_t FROM TB_CLV_PET p JOIN TB_CLV_RACA r ON r.id_raca = p.id_raca
                 WHERE p.id_pet = p_pet;
            WHEN 'GRUPO_RACA' THEN
                SELECT r.ds_grupo_raca INTO v_t FROM TB_CLV_PET p JOIN TB_CLV_RACA r ON r.id_raca = p.id_raca
                 WHERE p.id_pet = p_pet;
            ELSE
                RETURN FALSE;
        END CASE;

        IF v_numerico THEN
            IF v_num IS NULL THEN
                RETURN FALSE;
            END IF;
            RETURN CASE c.ds_operador
                       WHEN 'BETWEEN' THEN v_num >= NVL(v_min, -1E9) AND v_num <= NVL(v_max, 1E9)
                       WHEN 'GTE'     THEN v_num >= v_min
                       WHEN 'LTE'     THEN v_num <= NVL(v_max, v_min)
                       WHEN 'EQ'      THEN v_num = v_min
                       ELSE FALSE
                   END;
        END IF;

        IF v_t IS NULL THEN
            RETURN FALSE;
        END IF;
        RETURN CASE c.ds_operador
                   WHEN 'EQ'     THEN UPPER(v_t) = UPPER(c.ds_valor_texto)
                   WHEN 'NEQ'    THEN UPPER(v_t) <> UPPER(c.ds_valor_texto)
                   WHEN 'IN'     THEN INSTR(',' || UPPER(c.ds_valor_texto) || ',', ',' || UPPER(v_t) || ',') > 0
                   WHEN 'NOT_IN' THEN INSTR(',' || UPPER(c.ds_valor_texto) || ',', ',' || UPPER(v_t) || ',') = 0
                   ELSE FALSE
               END;
    END condicao_ok;

    -- Alguma regra do gatilho, de versão ainda não coberta, aceita o pet?
    -- Mesmo recorte e mesma guarda de cobertura do GERAR_OBRIGACOES.
    FUNCTION candidato(p_pet NUMBER, p_gatilho VARCHAR2, p_contexto VARCHAR2) RETURN BOOLEAN IS
        v_ok     PLS_INTEGER;
        v_total  PLS_INTEGER;
        v_coberto NUMBER;
    BEGIN
        FOR reg IN (SELECT r.id_regra_ativacao, r.ds_operador_logico, v.id_versao_protocolo
                      FROM TB_CLV_REGRA_ATIVACAO r
                      JOIN TB_CLV_VERSAO_PROTOCOLO v ON v.id_versao_protocolo = r.id_versao_protocolo
                      JOIN TB_CLV_PROTOCOLO p        ON p.id_protocolo = v.id_protocolo
                     WHERE r.ds_evento_gatilho = p_gatilho
                       AND v.ds_status = 'VIGENTE' AND p.fl_ativo = 'S'
                       AND (p.id_clinica IS NULL OR p.id_clinica = v_clinica)) LOOP
            SELECT COUNT(*) INTO v_coberto
              FROM TB_CLV_OBRIGACAO
             WHERE id_pet = p_pet AND id_versao_protocolo = reg.id_versao_protocolo
               AND dt_prevista >= v_hoje AND ds_status NOT IN ('CANCELADA', 'PERDIDA');
            CONTINUE WHEN v_coberto > 0;

            v_ok := 0;
            v_total := 0;
            FOR c IN (SELECT id_condicao_prot FROM TB_CLV_CONDICAO_PROT
                       WHERE id_regra_ativacao = reg.id_regra_ativacao) LOOP
                v_total := v_total + 1;
                IF condicao_ok(c.id_condicao_prot, p_pet, p_contexto) THEN
                    v_ok := v_ok + 1;
                END IF;
            END LOOP;

            IF v_total = 0
               OR (reg.ds_operador_logico = 'AND' AND v_ok = v_total)
               OR (reg.ds_operador_logico <> 'AND' AND v_ok > 0) THEN
                RETURN TRUE;
            END IF;
        END LOOP;
        RETURN FALSE;
    END candidato;

    PROCEDURE contar(o_obr OUT NUMBER, o_aud OUT NUMBER, o_out OUT NUMBER, o_log OUT NUMBER) IS
    BEGIN
        SELECT COUNT(*) INTO o_obr FROM TB_CLV_OBRIGACAO;
        SELECT COUNT(*) INTO o_aud FROM TB_CLV_AUDIT_OBRIGACAO;
        SELECT COUNT(*) INTO o_out FROM TB_CLV_OUTBOX_EVENT;
        SELECT COUNT(*) INTO o_log FROM TB_CLV_LOG_ERRO;
    END contar;

BEGIN
    IF DBMS_TRANSACTION.LOCAL_TRANSACTION_ID IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20950,
            'Ha transacao aberta nesta sessao. Faca COMMIT ou ROLLBACK do seu trabalho antes do ensaio.');
    END IF;

    BEGIN
        SELECT id_clinica INTO v_clinica FROM TB_CLV_VETERINARIO WHERE LOWER(ds_email) = c_login_helena;
    EXCEPTION
        WHEN NO_DATA_FOUND OR TOO_MANY_ROWS THEN
            RAISE_APPLICATION_ERROR(-20951, 'Login ' || c_login_helena || ' nao aponta para uma unica clinica.');
    END;

    contar(v_obr_antes, v_aud_antes, v_out_antes, v_log_antes);

    DBMS_OUTPUT.PUT_LINE('Clinica da Helena: id ' || v_clinica || ' | data de referencia: '
                         || TO_CHAR(v_hoje, 'YYYY-MM-DD'));
    DBMS_OUTPUT.PUT_LINE('ANTES  obrigacao=' || v_obr_antes || ' audit=' || v_aud_antes
                         || ' outbox=' || v_out_antes || ' log_erro=' || v_log_antes);
    DBMS_OUTPUT.PUT_LINE('combo | id_pet | pet | tutor | especie/raca | porte | meses | peso | castrado'
                         || ' | gatilho | contexto | geradas | protocolos | 1a data');

    FOR i IN 1 .. c_gatilhos.COUNT LOOP
        v_achados := 0;

        FOR pet IN (SELECT p.id_pet, p.nm_pet, t.nm_tutor, e.nm_especie, r.nm_raca, p.ds_porte,
                           NVL(p.fl_castrado, 'N') AS castrado,
                           FLOOR(MONTHS_BETWEEN(v_hoje, p.dt_nascimento)) AS meses,
                           (SELECT MAX(a.nr_peso_kg) KEEP (DENSE_RANK FIRST ORDER BY c.dt_consulta DESC)
                              FROM TB_CLV_ANAMNESE a JOIN TB_CLV_CONSULTA c ON c.id_consulta = a.id_consulta
                             WHERE c.id_pet = p.id_pet AND c.dt_consulta <= v_hoje
                               AND a.nr_peso_kg IS NOT NULL) AS peso
                      FROM TB_CLV_PET p
                      JOIN TB_CLV_TUTOR t   ON t.id_tutor = p.id_tutor
                      JOIN TB_CLV_RACA r    ON r.id_raca = p.id_raca
                      JOIN TB_CLV_ESPECIE e ON e.id_especie = r.id_especie
                     WHERE p.id_clinica = v_clinica
                       AND NVL(p.ds_status_pet, 'ATIVO') <> 'INATIVO'
                       AND LOWER(t.ds_email) <> c_tutor_agente
                     ORDER BY p.id_pet) LOOP
            EXIT WHEN v_achados >= c_por_combo;
            CONTINUE WHEN v_usados.EXISTS(pet.id_pet);
            CONTINUE WHEN NOT candidato(pet.id_pet, c_gatilhos(i), c_contextos(i));

            v_achados := v_achados + 1;
            v_usados(pet.id_pet) := TRUE;
            v_n := v_n + 1;

            SAVEPOINT ensaio_demo;

            PKG_CLV_MOTOR.GERAR_OBRIGACOES(
                p_id_clinica      => v_clinica,
                p_id_pet          => pet.id_pet,
                p_id_consulta     => NULL,
                p_evento_gatilho  => c_gatilhos(i),
                p_correlation_id  => c_marca || v_n,
                p_data_referencia => v_hoje,
                p_contexto        => c_contextos(i),
                o_qtd_criadas     => v_qtd);

            SELECT LISTAGG(codigo, ',') WITHIN GROUP (ORDER BY codigo), MIN(primeira)
              INTO v_protocolos, v_primeira
              FROM (SELECT pr.ds_codigo AS codigo, MIN(o.dt_prevista) AS primeira
                      FROM TB_CLV_OBRIGACAO o
                      JOIN TB_CLV_VERSAO_PROTOCOLO v ON v.id_versao_protocolo = o.id_versao_protocolo
                      JOIN TB_CLV_PROTOCOLO pr       ON pr.id_protocolo = v.id_protocolo
                     WHERE o.ds_correlation_id = c_marca || v_n
                     GROUP BY pr.ds_codigo);

            ROLLBACK TO SAVEPOINT ensaio_demo;

            DBMS_OUTPUT.PUT_LINE(i || ' | ' || pet.id_pet || ' | ' || pet.nm_pet || ' | ' || pet.nm_tutor
                || ' | ' || pet.nm_especie || '/' || pet.nm_raca || ' | ' || pet.ds_porte
                || ' | ' || pet.meses || ' | ' || NVL(TO_CHAR(pet.peso), '-') || ' | ' || pet.castrado
                || ' | ' || c_gatilhos(i) || ' | ' || NVL(c_contextos(i), 'Nenhum')
                || ' | ' || v_qtd || ' | ' || NVL(v_protocolos, '-')
                || ' | ' || NVL(TO_CHAR(v_primeira, 'YYYY-MM-DD'), '-'));
        END LOOP;

        IF v_achados = 0 THEN
            DBMS_OUTPUT.PUT_LINE(i || ' | nenhum candidato para ' || c_gatilhos(i)
                                 || ' / ' || NVL(c_contextos(i), 'Nenhum'));
        END IF;
    END LOOP;

    ROLLBACK;

    contar(v_obr_depois, v_aud_depois, v_out_depois, v_log_depois);
    SELECT COUNT(*) INTO v_marcadas FROM TB_CLV_OBRIGACAO WHERE ds_correlation_id LIKE c_marca || '%';

    DBMS_OUTPUT.PUT_LINE('DEPOIS obrigacao=' || v_obr_depois || ' audit=' || v_aud_depois
                         || ' outbox=' || v_out_depois || ' log_erro=' || v_log_depois
                         || ' | obrigacoes do ensaio que ficaram: ' || v_marcadas);
    DBMS_OUTPUT.PUT_LINE(CASE
        WHEN v_marcadas = 0 AND v_obr_antes = v_obr_depois AND v_aud_antes = v_aud_depois
             AND v_out_antes = v_out_depois AND v_log_antes = v_log_depois
        THEN 'OK: nada do ensaio ficou no banco.'
        WHEN v_marcadas = 0
        THEN 'ATENCAO: contagens mudaram, mas nenhuma obrigacao do ensaio ficou. Outra sessao (o site, a varredura) gravou no meio? Rode de novo.'
        ELSE 'DIVERGENCIA: ' || v_marcadas || ' obrigacoes do ensaio ficaram. Mande esta saida antes de qualquer outra coisa.'
    END);
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/

-- Conferência fora do bloco, na mesma sessão: tem que repetir o DEPOIS acima.
ROLLBACK;
SELECT (SELECT COUNT(*) FROM TB_CLV_OBRIGACAO)       AS obrigacao,
       (SELECT COUNT(*) FROM TB_CLV_AUDIT_OBRIGACAO) AS auditoria,
       (SELECT COUNT(*) FROM TB_CLV_OUTBOX_EVENT)    AS outbox,
       (SELECT COUNT(*) FROM TB_CLV_LOG_ERRO)        AS log_erro,
       (SELECT COUNT(*) FROM TB_CLV_OBRIGACAO WHERE ds_correlation_id LIKE 'ENSAIO-DEMO-%') AS do_ensaio
  FROM dual;
