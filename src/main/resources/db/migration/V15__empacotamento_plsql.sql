-- ============================================================
-- CLYVO VET — V15 — Empacotamento do PL/SQL
--
-- As 19 rotinas soltas da V7 e da V8 passam a morar em quatro pacotes. Muda
-- o endereço, não o comportamento: cada corpo foi transplantado da sua única
-- definição (nenhuma foi redefinida depois da V7/V8), e só mudaram o nome, as
-- chamadas internas que agora apontam para membros de pacote e o bloco de
-- exceção.
--
--   PKG_CLV_LOG        — o log de erro em transação autônoma
--   PKG_CLV_MOTOR      — geração de obrigação e máquina de estados
--   PKG_CLV_AUDITORIA  — trilha encadeada por hash e sua verificação
--   PKG_CLV_SEED       — gerador da base de demonstração
--
-- O trigger TR_CLV_AUDIT_OBRIGACAO continua existindo — trigger não mora em
-- pacote —, mas só monta o JSON de :OLD/:NEW e delega o hash e o insert para
-- PKG_CLV_AUDITORIA.REGISTRAR_TRANSICAO.
--
-- TRATAMENTO DE EXCEÇÃO. Toda rotina pública termina com WHEN OTHERS → log em
-- PKG_CLV_LOG → RAISE. As exceções de negócio continuam com o mesmo código
-- (-20010 transição inválida, -20011 obrigação não encontrada): é a faixa
-- -20000..-20999 que MotorProtocolo traduz em 409. Duas exceções à regra,
-- de propósito:
--   * REGISTRAR_ERRO engole o próprio erro: log nunca derruba a operação
--     principal, e não há onde logar a falha do log.
--   * AVALIA_CONDICAO e AVALIA_REGRA (privadas) devolvem 'N' em erro. Uma
--     condição que falha não casa a regra; ela não aborta a geração inteira.
--
-- A FÓRMULA DO HASH É INTOCÁVEL. A concatenação em REGISTRAR_TRANSICAO e em
-- VERIFICA_CADEIA é cópia literal da V7. Mudar um caractere quebra a
-- verificação de todo o histórico já gravado.
--
-- POR QUE TRÊS CHECAGENS. DDL no Oracle faz commit implícito, e o Flyway trata
-- erro de compilação de PL/SQL como warning (ORA-17110), não como falha. Sem
-- os blocos de conferência, um body quebrado passaria e só apareceria no
-- primeiro INSERT em TB_CLV_OBRIGACAO.
--   0. antes de qualquer DDL: nenhum objeto fora dos 20 depende dos nomes
--      antigos (no Oracle da FIAP, um objeto criado à mão ficaria INVALID);
--   3. depois dos bodies e ANTES de tocar no trigger e nas rotinas antigas:
--      se um pacote não compilou, aborta com o banco ainda funcionando;
--   6. no fim: pacotes e trigger VALID e nenhuma rotina antiga sobrando.
--
-- Ordem: 0 checagem → 1 specs → 2 bodies → 3 checagem → 4 trigger →
--        5 DROP das rotinas soltas → 6 checagem final.
--
-- Nenhuma rotina de seed é executada aqui.
-- ============================================================


-- ------------------------------------------------------------
-- 0. Ninguém de fora depende das rotinas que vão sumir
-- ------------------------------------------------------------
DECLARE
    v_lista VARCHAR2(4000);
BEGIN
    SELECT LISTAGG(d.type || ' ' || d.name || ' -> ' || d.referenced_name, ', ')
             WITHIN GROUP (ORDER BY d.name, d.referenced_name)
      INTO v_lista
      FROM USER_DEPENDENCIES d
     WHERE d.referenced_owner = USER
       AND d.referenced_type IN ('PROCEDURE','FUNCTION')
       AND d.referenced_name IN (
             'PR_CLV_LOG_ERRO',
             'FN_CLV_IDADE_MESES','FN_CLV_PESO_ATUAL','FN_CLV_FASE_VIDA',
             'FN_CLV_CALCULAR_DATA','FN_CLV_GRUPO_CONTROLE',
             'FN_CLV_AVALIA_CONDICAO','FN_CLV_AVALIA_REGRA',
             'FN_CLV_VERIFICA_CADEIA',
             'PR_CLV_GERAR_OBRIGACOES','PR_CLV_TRANSITAR_OBRIGACAO',
             'FN_CLV_SEED_CONSULTA','PR_CLV_SEED_LIMPAR','PR_CLV_SEED_BASE',
             'PR_CLV_SEED_POPULACAO','PR_CLV_SEED_GERAR','PR_CLV_SEED_DESFECHOS',
             'PR_CLV_SEED_HEROIS','PR_CLV_SEED_EXECUTAR')
       AND d.name NOT IN (
             'PR_CLV_LOG_ERRO',
             'FN_CLV_IDADE_MESES','FN_CLV_PESO_ATUAL','FN_CLV_FASE_VIDA',
             'FN_CLV_CALCULAR_DATA','FN_CLV_GRUPO_CONTROLE',
             'FN_CLV_AVALIA_CONDICAO','FN_CLV_AVALIA_REGRA',
             'FN_CLV_VERIFICA_CADEIA',
             'PR_CLV_GERAR_OBRIGACOES','PR_CLV_TRANSITAR_OBRIGACAO',
             'FN_CLV_SEED_CONSULTA','PR_CLV_SEED_LIMPAR','PR_CLV_SEED_BASE',
             'PR_CLV_SEED_POPULACAO','PR_CLV_SEED_GERAR','PR_CLV_SEED_DESFECHOS',
             'PR_CLV_SEED_HEROIS','PR_CLV_SEED_EXECUTAR',
             'TR_CLV_AUDIT_OBRIGACAO');

    IF v_lista IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20900,
            'V15 abortada antes de qualquer DDL: objetos fora do empacotamento '
            || 'dependem das rotinas soltas: ' || v_lista);
    END IF;
END;
/


-- ------------------------------------------------------------
-- 1. Specs
-- ------------------------------------------------------------

-- PKG_CLV_LOG
-- O log de erro de todas as rotinas do Clyvo. Grava em TB_CLV_LOG_ERRO numa
-- transação autônoma: o registro sobrevive ao ROLLBACK da operação que falhou,
-- que é justamente quando ele importa. Vem primeiro porque todos os outros
-- pacotes o chamam no handler de exceção.
CREATE OR REPLACE PACKAGE PKG_CLV_LOG AS

    PROCEDURE REGISTRAR_ERRO (
        p_procedure IN VARCHAR2,
        p_codigo    IN NUMBER,
        p_mensagem  IN VARCHAR2,
        p_ambiente  IN VARCHAR2 DEFAULT 'DEV'
    );

END PKG_CLV_LOG;
/

-- PKG_CLV_MOTOR
-- O motor de protocolo: decide quando um evento clínico gera obrigação e
-- qual transição de status é legal. A aplicação só chama GERAR_OBRIGACOES e
-- TRANSITAR_OBRIGACAO; nenhuma dessas regras existe em Java.
--
-- Públicas: as duas procedures que o Java chama, GRUPO_CONTROLE (o teste do
-- sorteio a chama direto) e FASE_VIDA (o seed a chama). O resto — avaliação
-- de regra e condição, cálculo de data, idade e peso — é detalhe interno do
-- motor e fica privado no body.
CREATE OR REPLACE PACKAGE PKG_CLV_MOTOR AS

    FUNCTION FASE_VIDA (
        p_id_pet   IN NUMBER,
        p_data_ref IN DATE DEFAULT SYSDATE
    ) RETURN VARCHAR2;

    FUNCTION GRUPO_CONTROLE (
        p_id_clinica     IN  NUMBER,
        p_id_pet         IN  NUMBER,
        p_versao_sorteio IN  NUMBER DEFAULT 1,
        p_percentual     IN  NUMBER DEFAULT 10,
        o_seed           OUT VARCHAR2,
        o_hash           OUT VARCHAR2
    ) RETURN CHAR;

    PROCEDURE GERAR_OBRIGACOES (
        p_id_clinica      IN  NUMBER,
        p_id_pet          IN  NUMBER,
        p_id_consulta     IN  NUMBER,
        p_evento_gatilho  IN  VARCHAR2,
        p_correlation_id  IN  VARCHAR2 DEFAULT NULL,
        p_data_referencia IN  DATE     DEFAULT SYSDATE,
        p_contexto        IN  VARCHAR2 DEFAULT NULL,
        p_horizonte_dias  IN  NUMBER   DEFAULT 400,
        o_qtd_criadas     OUT NUMBER
    );

    PROCEDURE TRANSITAR_OBRIGACAO (
        p_id_obrigacao   IN NUMBER,
        p_novo_status    IN VARCHAR2,
        p_motivo         IN VARCHAR2 DEFAULT NULL,
        p_usuario        IN VARCHAR2 DEFAULT NULL,
        p_causation_id   IN VARCHAR2 DEFAULT NULL,
        p_id_agendamento IN NUMBER   DEFAULT NULL,
        p_id_consulta    IN NUMBER   DEFAULT NULL,
        p_valor          IN NUMBER   DEFAULT NULL
    );

END PKG_CLV_MOTOR;
/

-- PKG_CLV_AUDITORIA
-- A trilha de auditoria encadeada de TB_CLV_OBRIGACAO. Cada elo guarda o hash
-- SHA-256 do anterior, por obrigação: alterar ou apagar uma linha da trilha
-- quebra a cadeia dali em diante, e VERIFICA_CADEIA aponta onde.
--
-- REGISTRAR_TRANSICAO é chamada pelo trigger TR_CLV_AUDIT_OBRIGACAO, que só
-- monta o JSON de :OLD/:NEW. VERIFICA_CADEIA é pública e pode ser usada em
-- SELECT.
CREATE OR REPLACE PACKAGE PKG_CLV_AUDITORIA AS

    PROCEDURE REGISTRAR_TRANSICAO (
        p_operacao     IN VARCHAR2,
        p_id_obrigacao IN NUMBER,
        p_id_clinica   IN NUMBER,
        p_valor_old    IN VARCHAR2,
        p_valor_new    IN VARCHAR2
    );

    FUNCTION VERIFICA_CADEIA (
        p_id_obrigacao IN NUMBER DEFAULT NULL
    ) RETURN VARCHAR2;

END PKG_CLV_AUDITORIA;
/

-- PKG_CLV_SEED
-- Gerador da base de demonstração: clínicas, tutores, pets, consultas e o
-- desfecho de 18 meses de obrigações, tudo passando pelo PKG_CLV_MOTOR. Nada
-- aqui é chamado pela aplicação.
--
-- ATENÇÃO: EXECUTAR e LIMPAR apagam e reescrevem a base de demonstração.
-- NUNCA as execute no Oracle da FIAP — lá está o seed congelado da demo, do
-- app mobile e do vídeo de BD. Só em banco descartável (o oracle-db do
-- docker-compose):
--     BEGIN PKG_CLV_SEED.EXECUTAR(p_qtd => 400); END;
--     /
--
-- As etapas (base, heróis, população, desfechos) são privadas: rodá-las fora
-- da ordem de EXECUTAR produz uma base incoerente.
CREATE OR REPLACE PACKAGE PKG_CLV_SEED AS

    PROCEDURE EXECUTAR (
        p_qtd   IN NUMBER DEFAULT 400,
        p_meses IN NUMBER DEFAULT 18
    );

    PROCEDURE LIMPAR;

END PKG_CLV_SEED;
/


-- ------------------------------------------------------------
-- 2. Bodies
-- ------------------------------------------------------------

CREATE OR REPLACE PACKAGE BODY PKG_CLV_LOG AS

    PROCEDURE REGISTRAR_ERRO (
        p_procedure IN VARCHAR2,
        p_codigo    IN NUMBER,
        p_mensagem  IN VARCHAR2,
        p_ambiente  IN VARCHAR2 DEFAULT 'DEV'
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
    BEGIN
        INSERT INTO TB_CLV_LOG_ERRO (nm_procedure, nm_usuario, dt_ocorrencia,
                                     nr_codigo_erro, ds_mensagem_erro, ds_ambiente)
        VALUES (p_procedure, USER, SYSTIMESTAMP,
                p_codigo, SUBSTR(p_mensagem,1,4000), p_ambiente);
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;   -- log nunca derruba a operação principal
    END REGISTRAR_ERRO;

END PKG_CLV_LOG;
/

CREATE OR REPLACE PACKAGE BODY PKG_CLV_MOTOR AS

    -- --------------------------------------------------------
    -- Funções de apoio (privadas, exceto FASE_VIDA e GRUPO_CONTROLE)
    -- --------------------------------------------------------

    FUNCTION IDADE_MESES (
        p_id_pet   IN NUMBER,
        p_data_ref IN DATE DEFAULT SYSDATE
    ) RETURN NUMBER IS
        v_nasc DATE;
    BEGIN
        SELECT dt_nascimento INTO v_nasc FROM TB_CLV_PET WHERE id_pet = p_id_pet;
        IF v_nasc IS NULL THEN
            RETURN NULL;
        END IF;
        RETURN FLOOR(MONTHS_BETWEEN(p_data_ref, v_nasc));
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN NULL;
    END IDADE_MESES;

    FUNCTION PESO_ATUAL (
        p_id_pet   IN NUMBER,
        p_data_ref IN DATE DEFAULT SYSDATE
    ) RETURN NUMBER IS
        v_peso NUMBER(5,2);
    BEGIN
        SELECT peso INTO v_peso
          FROM (SELECT a.nr_peso_kg AS peso
                  FROM TB_CLV_ANAMNESE a
                  JOIN TB_CLV_CONSULTA c ON c.id_consulta = a.id_consulta
                 WHERE c.id_pet = p_id_pet
                   AND c.dt_consulta <= p_data_ref
                   AND a.nr_peso_kg IS NOT NULL
                 ORDER BY c.dt_consulta DESC)
         WHERE ROWNUM = 1;
        RETURN v_peso;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN NULL;
    END PESO_ATUAL;

    FUNCTION FASE_VIDA (
        p_id_pet   IN NUMBER,
        p_data_ref IN DATE DEFAULT SYSDATE
    ) RETURN VARCHAR2 IS
        v_idade   NUMBER;
        v_porte   VARCHAR2(10);
        v_especie VARCHAR2(60);
        v_senior  NUMBER;
        v_adulto  NUMBER;
    BEGIN
        SELECT p.ds_porte, UPPER(e.nm_especie)
          INTO v_porte, v_especie
          FROM TB_CLV_PET p
          JOIN TB_CLV_RACA    r ON r.id_raca    = p.id_raca
          JOIN TB_CLV_ESPECIE e ON e.id_especie = r.id_especie
         WHERE p.id_pet = p_id_pet;

        v_idade := IDADE_MESES(p_id_pet, p_data_ref);
        IF v_idade IS NULL THEN
            RETURN 'INDEFINIDA';
        END IF;

        IF v_especie LIKE 'FEL%' THEN
            v_adulto := 12;
            v_senior := 84;
        ELSE
            v_adulto := 12;
            v_senior := CASE v_porte
                            WHEN 'GIGANTE' THEN 60
                            WHEN 'GRANDE'  THEN 72
                            WHEN 'MEDIO'   THEN 84
                            ELSE 96
                        END;
        END IF;

        IF    v_idade < 6        THEN RETURN 'FILHOTE';
        ELSIF v_idade < v_adulto THEN RETURN 'JOVEM';
        ELSIF v_idade < v_senior THEN RETURN 'ADULTO';
        ELSE                          RETURN 'SENIOR';
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN 'INDEFINIDA';
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_MOTOR.FASE_VIDA', SQLCODE,
                                       'pet=' || p_id_pet || ' ' || SQLERRM);
            RAISE;
    END FASE_VIDA;

    FUNCTION CALCULAR_DATA (
        p_id_etapa        IN NUMBER,
        p_data_referencia IN DATE,
        p_data_anterior   IN DATE DEFAULT NULL
    ) RETURN DATE IS
        v_tipo   VARCHAR2(20);
        v_offset NUMBER;
        v_base   DATE;
    BEGIN
        SELECT ds_tipo_intervalo, nr_offset_dias
          INTO v_tipo, v_offset
          FROM TB_CLV_ETAPA_PROTOCOLO
         WHERE id_etapa = p_id_etapa;

        v_base := CASE v_tipo
                      WHEN 'APOS_REFERENCIA' THEN p_data_referencia
                      ELSE NVL(p_data_anterior, p_data_referencia)
                  END;

        RETURN TRUNC(v_base) + NVL(v_offset,0);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN NULL;
    END CALCULAR_DATA;

    FUNCTION GRUPO_CONTROLE (
        p_id_clinica     IN  NUMBER,
        p_id_pet         IN  NUMBER,
        p_versao_sorteio IN  NUMBER DEFAULT 1,
        p_percentual     IN  NUMBER DEFAULT 10,
        o_seed           OUT VARCHAR2,
        o_hash           OUT VARCHAR2
    ) RETURN CHAR IS
        v_bucket NUMBER;
    BEGIN
        o_seed := 'CLV:v' || p_versao_sorteio || ':' || p_id_clinica || ':' || p_id_pet;

        SELECT RAWTOHEX(STANDARD_HASH(o_seed, 'SHA256')) INTO o_hash FROM dual;

        v_bucket := MOD(TO_NUMBER(SUBSTR(o_hash,1,8), 'XXXXXXXX'), 1000);

        RETURN CASE WHEN v_bucket < (p_percentual * 10) THEN 'S' ELSE 'N' END;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_MOTOR.GRUPO_CONTROLE', SQLCODE,
                                       'clinica=' || p_id_clinica || ' pet=' || p_id_pet || ' ' || SQLERRM);
            RAISE;
    END GRUPO_CONTROLE;

    FUNCTION AVALIA_CONDICAO (
        p_id_condicao IN NUMBER,
        p_id_pet      IN NUMBER,
        p_data_ref    IN DATE     DEFAULT SYSDATE,
        p_contexto    IN VARCHAR2 DEFAULT NULL
    ) RETURN CHAR IS
        v_attr     VARCHAR2(20);
        v_oper     VARCHAR2(10);
        v_texto    VARCHAR2(400);
        v_min      NUMBER;
        v_max      NUMBER;
        v_porte_fl CHAR(1);

        v_porte    VARCHAR2(10);
        v_valor_n  NUMBER;
        v_valor_t  VARCHAR2(400);
        v_qtd      NUMBER;
        v_numerico BOOLEAN := FALSE;
    BEGIN
        SELECT ds_atributo, ds_operador, ds_valor_texto,
               nr_valor_min, nr_valor_max, fl_por_porte
          INTO v_attr, v_oper, v_texto, v_min, v_max, v_porte_fl
          FROM TB_CLV_CONDICAO_PROT
         WHERE id_condicao_prot = p_id_condicao;

        SELECT ds_porte INTO v_porte FROM TB_CLV_PET WHERE id_pet = p_id_pet;

        -- Limiar por porte sobrescreve min/max da condição.
        IF v_porte_fl = 'S' THEN
            BEGIN
                SELECT nr_valor_min, nr_valor_max
                  INTO v_min, v_max
                  FROM TB_CLV_PARAM_PORTE
                 WHERE id_condicao_prot = p_id_condicao
                   AND ds_porte = v_porte;
            EXCEPTION
                WHEN NO_DATA_FOUND THEN RETURN 'N';
            END;
        END IF;

        CASE v_attr
            WHEN 'ESPECIE' THEN
                SELECT UPPER(e.nm_especie) INTO v_valor_t
                  FROM TB_CLV_PET p
                  JOIN TB_CLV_RACA    r ON r.id_raca    = p.id_raca
                  JOIN TB_CLV_ESPECIE e ON e.id_especie = r.id_especie
                 WHERE p.id_pet = p_id_pet;
                -- tolerante a CANINA / CANINO / CAO
                RETURN CASE WHEN SUBSTR(v_valor_t,1,3) = SUBSTR(UPPER(v_texto),1,3)
                            THEN 'S' ELSE 'N' END;

            WHEN 'RACA' THEN
                SELECT r.nm_raca INTO v_valor_t
                  FROM TB_CLV_PET p JOIN TB_CLV_RACA r ON r.id_raca = p.id_raca
                 WHERE p.id_pet = p_id_pet;

            WHEN 'GRUPO_RACA' THEN
                SELECT r.ds_grupo_raca INTO v_valor_t
                  FROM TB_CLV_PET p JOIN TB_CLV_RACA r ON r.id_raca = p.id_raca
                 WHERE p.id_pet = p_id_pet;

            WHEN 'BRAQUICEFALICO' THEN
                SELECT NVL(r.fl_braquicefalico,'N') INTO v_valor_t
                  FROM TB_CLV_PET p JOIN TB_CLV_RACA r ON r.id_raca = p.id_raca
                 WHERE p.id_pet = p_id_pet;

            WHEN 'PORTE' THEN
                v_valor_t := v_porte;

            WHEN 'SEXO' THEN
                SELECT ds_sexo INTO v_valor_t FROM TB_CLV_PET WHERE id_pet = p_id_pet;

            WHEN 'CASTRADO' THEN
                SELECT NVL(fl_castrado,'N') INTO v_valor_t
                  FROM TB_CLV_PET WHERE id_pet = p_id_pet;

            WHEN 'IDADE_MESES' THEN
                v_valor_n  := IDADE_MESES(p_id_pet, p_data_ref);
                v_numerico := TRUE;

            WHEN 'PESO_KG' THEN
                v_valor_n  := PESO_ATUAL(p_id_pet, p_data_ref);
                v_numerico := TRUE;

            WHEN 'CONDICAO' THEN
                SELECT COUNT(*) INTO v_qtd
                  FROM TB_CLV_CONDICAO_PET cp
                  JOIN TB_CLV_TIPO_CONDICAO tc ON tc.id_tipo_condicao = cp.id_tipo_condicao
                 WHERE cp.id_pet = p_id_pet
                   AND NVL(cp.fl_ativo,'S') = 'S'
                   AND cp.dt_diagnostico <= p_data_ref
                   AND INSTR(',' || UPPER(v_texto) || ',',
                             ',' || UPPER(tc.nm_condicao) || ',') > 0;
                RETURN CASE WHEN v_qtd > 0 THEN 'S' ELSE 'N' END;

            WHEN 'PROCEDIMENTO' THEN
                IF p_contexto IS NULL THEN
                    RETURN 'N';
                END IF;
                RETURN CASE WHEN INSTR(',' || UPPER(v_texto) || ',',
                                       ',' || UPPER(p_contexto) || ',') > 0
                            THEN 'S' ELSE 'N' END;
            ELSE
                RETURN 'N';
        END CASE;

        -- Comparação numérica
        IF v_numerico THEN
            IF v_valor_n IS NULL THEN
                RETURN 'N';   -- sem dado, condição não é satisfeita
            END IF;
            CASE v_oper
                WHEN 'BETWEEN' THEN
                    RETURN CASE WHEN v_valor_n >= NVL(v_min,-1E9)
                                 AND v_valor_n <= NVL(v_max, 1E9)
                                THEN 'S' ELSE 'N' END;
                WHEN 'GTE' THEN
                    RETURN CASE WHEN v_valor_n >= v_min THEN 'S' ELSE 'N' END;
                WHEN 'LTE' THEN
                    RETURN CASE WHEN v_valor_n <= NVL(v_max, v_min) THEN 'S' ELSE 'N' END;
                WHEN 'EQ' THEN
                    RETURN CASE WHEN v_valor_n = v_min THEN 'S' ELSE 'N' END;
                ELSE
                    RETURN 'N';
            END CASE;
        END IF;

        -- Comparação textual
        IF v_valor_t IS NULL THEN
            RETURN 'N';
        END IF;

        CASE v_oper
            WHEN 'EQ' THEN
                RETURN CASE WHEN UPPER(v_valor_t) = UPPER(v_texto) THEN 'S' ELSE 'N' END;
            WHEN 'NEQ' THEN
                RETURN CASE WHEN UPPER(v_valor_t) <> UPPER(v_texto) THEN 'S' ELSE 'N' END;
            WHEN 'IN' THEN
                RETURN CASE WHEN INSTR(',' || UPPER(v_texto) || ',',
                                       ',' || UPPER(v_valor_t) || ',') > 0
                            THEN 'S' ELSE 'N' END;
            WHEN 'NOT_IN' THEN
                RETURN CASE WHEN INSTR(',' || UPPER(v_texto) || ',',
                                       ',' || UPPER(v_valor_t) || ',') > 0
                            THEN 'N' ELSE 'S' END;
            ELSE
                RETURN 'N';
        END CASE;

    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_MOTOR.AVALIA_CONDICAO', SQLCODE,
                            'condicao=' || p_id_condicao || ' pet=' || p_id_pet || ' ' || SQLERRM);
            RETURN 'N';
    END AVALIA_CONDICAO;

    FUNCTION AVALIA_REGRA (
        p_id_regra IN NUMBER,
        p_id_pet   IN NUMBER,
        p_data_ref IN DATE     DEFAULT SYSDATE,
        p_contexto IN VARCHAR2 DEFAULT NULL
    ) RETURN CHAR IS
        v_oper  VARCHAR2(3);
        v_res   CHAR(1);
        v_total NUMBER := 0;
        v_ok    NUMBER := 0;
    BEGIN
        SELECT ds_operador_logico INTO v_oper
          FROM TB_CLV_REGRA_ATIVACAO WHERE id_regra_ativacao = p_id_regra;

        FOR c IN (SELECT id_condicao_prot FROM TB_CLV_CONDICAO_PROT
                   WHERE id_regra_ativacao = p_id_regra) LOOP
            v_total := v_total + 1;
            v_res := AVALIA_CONDICAO(c.id_condicao_prot, p_id_pet, p_data_ref, p_contexto);
            IF v_res = 'S' THEN
                v_ok := v_ok + 1;
            ELSIF v_oper = 'AND' THEN
                RETURN 'N';   -- curto-circuito
            END IF;
        END LOOP;

        IF v_total = 0 THEN
            RETURN 'S';
        END IF;

        RETURN CASE WHEN v_oper = 'AND' THEN
                        CASE WHEN v_ok = v_total THEN 'S' ELSE 'N' END
                    ELSE
                        CASE WHEN v_ok > 0 THEN 'S' ELSE 'N' END
               END;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_MOTOR.AVALIA_REGRA', SQLCODE,
                            'regra=' || p_id_regra || ' pet=' || p_id_pet || ' ' || SQLERRM);
            RETURN 'N';
    END AVALIA_REGRA;

    -- --------------------------------------------------------
    -- Procedures do motor
    -- --------------------------------------------------------

    PROCEDURE GERAR_OBRIGACOES (
        p_id_clinica      IN  NUMBER,
        p_id_pet          IN  NUMBER,
        p_id_consulta     IN  NUMBER,
        p_evento_gatilho  IN  VARCHAR2,
        p_correlation_id  IN  VARCHAR2 DEFAULT NULL,
        p_data_referencia IN  DATE     DEFAULT SYSDATE,
        p_contexto        IN  VARCHAR2 DEFAULT NULL,
        p_horizonte_dias  IN  NUMBER   DEFAULT 400,
        o_qtd_criadas     OUT NUMBER
    ) IS
        v_corr       VARCHAR2(36);
        v_grupo      CHAR(1);
        v_seed       VARCHAR2(200);
        v_hash       VARCHAR2(64);
        v_data       DATE;
        v_data_ant   DATE;
        v_limite     DATE;
        v_qtd_dup    NUMBER;
        v_ja_coberto NUMBER;
        v_ocorrencia NUMBER;
        v_id_obr     NUMBER(19);
        v_valor      NUMBER(10,2);
        v_ticket     NUMBER(10,2);

        TYPE t_datas IS TABLE OF DATE INDEX BY PLS_INTEGER;
        v_datas t_datas;
    BEGIN
        o_qtd_criadas := 0;
        v_limite := TRUNC(p_data_referencia) + p_horizonte_dias;
        v_corr   := NVL(p_correlation_id, RAWTOHEX(SYS_GUID()));

        SELECT NVL(nr_ticket_medio, 400) INTO v_ticket
          FROM TB_CLV_CLINICA WHERE id_clinica = p_id_clinica;

        v_grupo := GRUPO_CONTROLE(p_id_clinica, p_id_pet, 1, 10, v_seed, v_hash);

        FOR reg IN (
            SELECT r.id_regra_ativacao, v.id_versao_protocolo
              FROM TB_CLV_REGRA_ATIVACAO r
              JOIN TB_CLV_VERSAO_PROTOCOLO v ON v.id_versao_protocolo = r.id_versao_protocolo
              JOIN TB_CLV_PROTOCOLO p        ON p.id_protocolo        = v.id_protocolo
             WHERE r.ds_evento_gatilho = p_evento_gatilho
               AND v.ds_status = 'VIGENTE'
               AND p.fl_ativo  = 'S'
               AND (p.id_clinica IS NULL OR p.id_clinica = p_id_clinica)
             ORDER BY r.nr_prioridade
        ) LOOP

            -- (a) Guarda de cobertura: já existe obrigação viva e futura
            -- desta versão para este pet? Então o protocolo já está em
            -- andamento e não deve recomeçar.
            SELECT COUNT(*) INTO v_ja_coberto
              FROM TB_CLV_OBRIGACAO
             WHERE id_pet              = p_id_pet
               AND id_versao_protocolo = reg.id_versao_protocolo
               AND dt_prevista        >= TRUNC(p_data_referencia)
               AND ds_status NOT IN ('CANCELADA','PERDIDA');

            IF v_ja_coberto > 0 THEN
                CONTINUE;
            END IF;

            IF AVALIA_REGRA(reg.id_regra_ativacao, p_id_pet,
                            p_data_referencia, p_contexto) = 'S' THEN

                v_datas.DELETE;

                FOR et IN (SELECT id_etapa, nr_ordem, ds_tipo_intervalo,
                                  id_etapa_referencia, nr_offset_dias,
                                  nr_janela_antes, nr_janela_depois,
                                  nr_recorrencia_dias, nr_valor_referencia,
                                  nr_max_ocorrencias
                             FROM TB_CLV_ETAPA_PROTOCOLO
                            WHERE id_versao_protocolo = reg.id_versao_protocolo
                            ORDER BY nr_ordem) LOOP

                    v_data_ant := NULL;
                    IF et.ds_tipo_intervalo <> 'APOS_REFERENCIA'
                       AND et.id_etapa_referencia IS NOT NULL
                       AND v_datas.EXISTS(et.id_etapa_referencia) THEN
                        v_data_ant := v_datas(et.id_etapa_referencia);
                    END IF;

                    v_data := CALCULAR_DATA(et.id_etapa, p_data_referencia, v_data_ant);
                    v_datas(et.id_etapa) := v_data;

                    v_valor      := NVL(et.nr_valor_referencia, v_ticket);
                    v_ocorrencia := 1;

                    LOOP
                        EXIT WHEN v_data IS NULL OR v_data > v_limite;

                        -- (b) Limite de repetições da etapa recorrente
                        EXIT WHEN et.nr_max_ocorrencias IS NOT NULL
                              AND v_ocorrencia > et.nr_max_ocorrencias;

                        SELECT COUNT(*) INTO v_qtd_dup
                          FROM TB_CLV_OBRIGACAO
                         WHERE id_pet      = p_id_pet
                           AND id_etapa    = et.id_etapa
                           AND dt_prevista = v_data;

                        IF v_qtd_dup = 0 THEN
                            INSERT INTO TB_CLV_OBRIGACAO (
                                id_clinica, id_pet, id_versao_protocolo, id_etapa,
                                id_consulta_origem, dt_prevista,
                                dt_janela_inicio, dt_janela_fim, ds_status,
                                ds_correlation_id, fl_grupo_controle,
                                ds_sorteio_seed, ds_hash_sorteio, nr_versao_sorteio,
                                nr_valor_estimado
                            ) VALUES (
                                p_id_clinica, p_id_pet, reg.id_versao_protocolo, et.id_etapa,
                                p_id_consulta, v_data,
                                v_data - NVL(et.nr_janela_antes,7),
                                v_data + NVL(et.nr_janela_depois,30), 'PREVISTA',
                                v_corr, v_grupo,
                                v_seed, v_hash, 1,
                                v_valor
                            ) RETURNING id_obrigacao INTO v_id_obr;

                            INSERT INTO TB_CLV_OUTBOX_EVENT (
                                ds_event_uuid, ds_event_type, ds_correlation_id,
                                id_clinica, ds_agregado, nr_id_agregado, ds_payload
                            ) VALUES (
                                RAWTOHEX(SYS_GUID()), 'ObrigacaoGerada', v_corr,
                                p_id_clinica, 'OBRIGACAO', v_id_obr,
                                '{"idObrigacao":'     || v_id_obr    ||
                                ',"idPet":'           || p_id_pet    ||
                                ',"idEtapa":'         || et.id_etapa ||
                                ',"dtPrevista":"'     || TO_CHAR(v_data,'YYYY-MM-DD') ||
                                '","grupoControle":"' || v_grupo     ||
                                '","valorEstimado":'  || v_valor     || '}'
                            );

                            o_qtd_criadas := o_qtd_criadas + 1;
                        END IF;

                        EXIT WHEN et.nr_recorrencia_dias IS NULL;
                        v_data       := v_data + et.nr_recorrencia_dias;
                        v_ocorrencia := v_ocorrencia + 1;
                    END LOOP;

                END LOOP;
            END IF;
        END LOOP;

    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_MOTOR.GERAR_OBRIGACOES', SQLCODE,
                            'pet=' || p_id_pet || ' gatilho=' || p_evento_gatilho || ' ' || SQLERRM);
            RAISE;
    END GERAR_OBRIGACOES;

    PROCEDURE TRANSITAR_OBRIGACAO (
        p_id_obrigacao   IN NUMBER,
        p_novo_status    IN VARCHAR2,
        p_motivo         IN VARCHAR2 DEFAULT NULL,
        p_usuario        IN VARCHAR2 DEFAULT NULL,
        p_causation_id   IN VARCHAR2 DEFAULT NULL,
        p_id_agendamento IN NUMBER   DEFAULT NULL,
        p_id_consulta    IN NUMBER   DEFAULT NULL,
        p_valor          IN NUMBER   DEFAULT NULL
    ) IS
        v_status_atual VARCHAR2(12);
        v_id_clinica   NUMBER(19);
        v_corr         VARCHAR2(36);
        v_valido       BOOLEAN := FALSE;
    BEGIN
        -- FOR UPDATE serializa transições concorrentes na mesma obrigação.
        SELECT ds_status, id_clinica, ds_correlation_id
          INTO v_status_atual, v_id_clinica, v_corr
          FROM TB_CLV_OBRIGACAO
         WHERE id_obrigacao = p_id_obrigacao
           FOR UPDATE;

        -- Obrigação em grupo de controle pula NOTIFICADA: vai de PREVISTA
        -- direto a RESPONDIDA ou AGENDADA quando o tutor retorna sozinho.
        v_valido :=
            (v_status_atual = 'PREVISTA'   AND p_novo_status IN ('NOTIFICADA','RESPONDIDA','AGENDADA','PERDIDA','CANCELADA')) OR
            (v_status_atual = 'NOTIFICADA' AND p_novo_status IN ('RESPONDIDA','AGENDADA','PERDIDA','CANCELADA'))              OR
            (v_status_atual = 'RESPONDIDA' AND p_novo_status IN ('AGENDADA','PERDIDA','CANCELADA'))                           OR
            (v_status_atual = 'AGENDADA'   AND p_novo_status IN ('AGENDADA','CUMPRIDA','PERDIDA','CANCELADA'));

        IF NOT v_valido THEN
            RAISE_APPLICATION_ERROR(-20010,
                'Transicao invalida: ' || v_status_atual || ' -> ' || p_novo_status);
        END IF;

        UPDATE TB_CLV_OBRIGACAO
           SET ds_status           = p_novo_status,
               ds_causation_id     = NVL(p_causation_id, ds_causation_id),
               id_agendamento      = NVL(p_id_agendamento, id_agendamento),
               id_consulta_destino = NVL(p_id_consulta, id_consulta_destino),
               nr_valor_realizado  = CASE WHEN p_novo_status = 'CUMPRIDA'
                                          THEN NVL(p_valor, nr_valor_estimado)
                                          ELSE nr_valor_realizado END,
               dt_notificada = CASE WHEN p_novo_status = 'NOTIFICADA' THEN SYSTIMESTAMP ELSE dt_notificada END,
               dt_respondida = CASE WHEN p_novo_status = 'RESPONDIDA' THEN SYSTIMESTAMP ELSE dt_respondida END,
               dt_agendada   = CASE WHEN p_novo_status = 'AGENDADA'   THEN SYSTIMESTAMP ELSE dt_agendada   END,
               dt_cumprida   = CASE WHEN p_novo_status = 'CUMPRIDA'   THEN SYSTIMESTAMP ELSE dt_cumprida   END,
               dt_atualizacao = SYSTIMESTAMP
         WHERE id_obrigacao = p_id_obrigacao;

        INSERT INTO TB_CLV_OBRIGACAO_TRANSICAO (
            id_clinica, id_obrigacao, ds_status_de, ds_status_para,
            ds_motivo, ds_causation_id, nm_usuario
        ) VALUES (
            v_id_clinica, p_id_obrigacao, v_status_atual, p_novo_status,
            p_motivo, p_causation_id, NVL(p_usuario, USER)
        );

        INSERT INTO TB_CLV_OUTBOX_EVENT (
            ds_event_uuid, ds_event_type, ds_correlation_id, ds_causation_id,
            id_clinica, nm_usuario, ds_agregado, nr_id_agregado, ds_payload
        ) VALUES (
            RAWTOHEX(SYS_GUID()),
            'Obrigacao' || INITCAP(LOWER(p_novo_status)),
            v_corr, p_causation_id,
            v_id_clinica, NVL(p_usuario, USER), 'OBRIGACAO', p_id_obrigacao,
            '{"idObrigacao":' || p_id_obrigacao ||
            ',"de":"'   || v_status_atual || '"' ||
            ',"para":"' || p_novo_status  || '"}'
        );

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20011, 'Obrigacao nao encontrada: ' || p_id_obrigacao);
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO', SQLCODE,
                            'obrigacao=' || p_id_obrigacao || ' ' || SQLERRM);
            RAISE;
    END TRANSITAR_OBRIGACAO;

END PKG_CLV_MOTOR;
/

CREATE OR REPLACE PACKAGE BODY PKG_CLV_AUDITORIA AS

    -- Corpo transplantado do TR_CLV_AUDIT_OBRIGACAO da V7. Os parâmetros são
    -- copiados para variáveis com os nomes originais para que a expressão do
    -- hash continue sendo, caractere por caractere, a mesma da V7.
    PROCEDURE REGISTRAR_TRANSICAO (
        p_operacao     IN VARCHAR2,
        p_id_obrigacao IN NUMBER,
        p_id_clinica   IN NUMBER,
        p_valor_old    IN VARCHAR2,
        p_valor_new    IN VARCHAR2
    ) IS
        v_op       VARCHAR2(10)   := p_operacao;
        v_old      VARCHAR2(4000) := p_valor_old;
        v_new      VARCHAR2(4000) := p_valor_new;
        v_id_obr   NUMBER(19)     := p_id_obrigacao;
        v_clinica  NUMBER(19)     := p_id_clinica;
        v_anterior VARCHAR2(64);
        v_seq      NUMBER(19);
        v_hash     VARCHAR2(64);
    BEGIN
        BEGIN
            SELECT ds_hash_atual INTO v_anterior
              FROM (SELECT ds_hash_atual
                      FROM TB_CLV_AUDIT_OBRIGACAO
                     WHERE id_obrigacao = v_id_obr
                     ORDER BY nr_seq DESC)
             WHERE ROWNUM = 1;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN v_anterior := NULL;
        END;

        v_seq := SQ_CLV_AUDIT_OBRIGACAO.NEXTVAL;

        SELECT RAWTOHEX(STANDARD_HASH(
                   NVL(v_anterior,'GENESIS') || '|' || v_seq || '|' || v_id_obr ||
                   '|' || v_op || '|' || NVL(v_new, NVL(v_old,'-')), 'SHA256'))
          INTO v_hash FROM dual;

        INSERT INTO TB_CLV_AUDIT_OBRIGACAO (
            nr_seq, id_clinica, id_obrigacao, ds_operacao,
            ds_valor_old, ds_valor_new, nm_usuario,
            ds_hash_anterior, ds_hash_atual
        ) VALUES (
            v_seq, v_clinica, v_id_obr, v_op,
            v_old, v_new, USER,
            v_anterior, v_hash
        );
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_AUDITORIA.REGISTRAR_TRANSICAO', SQLCODE,
                            'obrigacao=' || p_id_obrigacao || ' op=' || p_operacao || ' ' || SQLERRM);
            RAISE;
    END REGISTRAR_TRANSICAO;

    FUNCTION VERIFICA_CADEIA (
        p_id_obrigacao IN NUMBER DEFAULT NULL
    ) RETURN VARCHAR2 IS
        v_obr_atual NUMBER(19) := -1;
        v_anterior  VARCHAR2(64);
        v_calculado VARCHAR2(64);
        v_conteudo  VARCHAR2(4000);
        v_qtd       NUMBER := 0;
    BEGIN
        FOR a IN (SELECT nr_seq, id_obrigacao, ds_operacao,
                         DBMS_LOB.SUBSTR(ds_valor_old, 3000, 1) AS v_old,
                         DBMS_LOB.SUBSTR(ds_valor_new, 3000, 1) AS v_new,
                         ds_hash_anterior, ds_hash_atual
                    FROM TB_CLV_AUDIT_OBRIGACAO
                   WHERE (p_id_obrigacao IS NULL OR id_obrigacao = p_id_obrigacao)
                   ORDER BY id_obrigacao, nr_seq) LOOP

            -- A cadeia é por obrigação: reinicia a cada troca.
            IF a.id_obrigacao <> v_obr_atual THEN
                v_obr_atual := a.id_obrigacao;
                v_anterior  := NULL;
            END IF;

            IF NVL(a.ds_hash_anterior, 'X') <> NVL(v_anterior, 'X') THEN
                RETURN 'QUEBRA DE ELO na obrigacao ' || a.id_obrigacao ||
                       ', seq ' || a.nr_seq;
            END IF;

            v_conteudo := NVL(v_anterior, 'GENESIS') || '|' || a.nr_seq || '|' ||
                          a.id_obrigacao || '|' || a.ds_operacao || '|' ||
                          NVL(a.v_new, NVL(a.v_old, '-'));

            SELECT RAWTOHEX(STANDARD_HASH(v_conteudo, 'SHA256'))
              INTO v_calculado FROM dual;

            IF v_calculado <> a.ds_hash_atual THEN
                RETURN 'CONTEUDO ADULTERADO na obrigacao ' || a.id_obrigacao ||
                       ', seq ' || a.nr_seq;
            END IF;

            v_anterior := a.ds_hash_atual;
            v_qtd      := v_qtd + 1;
        END LOOP;

        IF v_qtd = 0 THEN
            RETURN 'SEM REGISTROS';
        END IF;

        RETURN 'INTEGRA (' || v_qtd || ' registros verificados)';
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_AUDITORIA.VERIFICA_CADEIA', SQLCODE,
                            'obrigacao=' || p_id_obrigacao || ' ' || SQLERRM);
            RAISE;
    END VERIFICA_CADEIA;

END PKG_CLV_AUDITORIA;
/

-- Os membros vêm em ordem de dependência, sem declaração antecipada: o
-- PainelCoorteIntegracaoTest lê as taxas-alvo de DESFECHOS no USER_SOURCE
-- deste body e espera achar cada uma exatamente uma vez.
CREATE OR REPLACE PACKAGE BODY PKG_CLV_SEED AS

    PROCEDURE LIMPAR IS
    BEGIN
        DELETE FROM TB_CLV_AUDIT_OBRIGACAO;
        DELETE FROM TB_CLV_OBRIGACAO_TRANSICAO;
        DELETE FROM TB_CLV_OUTBOX_EVENT;
        DELETE FROM TB_CLV_OBRIGACAO;
        DELETE FROM TB_CLV_AGENDAMENTO;
        DELETE FROM TB_CLV_CONDICAO_PET;
        DELETE FROM TB_CLV_ALERGIA_PET;
        DELETE FROM TB_CLV_PRESCRICAO;
        DELETE FROM TB_CLV_EXAME;
        DELETE FROM TB_CLV_ANAMNESE;
        DELETE FROM TB_CLV_CONSULTA;
        DELETE FROM TB_CLV_PET;
        DELETE FROM TB_CLV_TUTOR;
        DELETE FROM TB_CLV_COLABORADOR;
        DELETE FROM TB_CLV_VETERINARIO;
        DELETE FROM TB_CLV_CLINICA;
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.LIMPAR', SQLCODE, SQLERRM);
            RAISE;
    END LIMPAR;

    PROCEDURE BASE IS
        v_a NUMBER(19);
        v_b NUMBER(19);
    BEGIN
        INSERT INTO TB_CLV_CLINICA (nm_clinica, ds_cnpj, ds_logradouro, ds_numero,
                                    ds_bairro, nm_cidade, sg_estado, nr_cep,
                                    nr_telefone, nr_ticket_medio)
        VALUES ('Clinica Vida Animal', '11222333000181', 'Av. Paulista', '1500',
                'Bela Vista', 'Sao Paulo', 'SP', '01310100', '1133334444', 420)
        RETURNING id_clinica INTO v_a;

        INSERT INTO TB_CLV_CLINICA (nm_clinica, ds_cnpj, ds_logradouro, ds_numero,
                                    ds_bairro, nm_cidade, sg_estado, nr_cep,
                                    nr_telefone, nr_ticket_medio)
        VALUES ('PetCare Zona Sul', '44555666000172', 'Av. Santo Amaro', '820',
                'Brooklin', 'Sao Paulo', 'SP', '04505000', '1155556666', 380)
        RETURNING id_clinica INTO v_b;

        INSERT INTO TB_CLV_VETERINARIO (nm_veterinario, nr_crmv, ds_especialidade,
                                        ds_email, ds_senha_hash, id_clinica)
        VALUES ('Dra. Helena Prado', 'SP-11001', 'Clinica geral',
                'helena@vidaanimal.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                v_a);

        INSERT INTO TB_CLV_VETERINARIO (nm_veterinario, nr_crmv, ds_especialidade,
                                        ds_email, ds_senha_hash, id_clinica)
        VALUES ('Dr. Rafael Nunes', 'SP-11002', 'Nefrologia',
                'rafael@vidaanimal.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                v_a);

        INSERT INTO TB_CLV_VETERINARIO (nm_veterinario, nr_crmv, ds_especialidade,
                                        ds_email, ds_senha_hash, id_clinica)
        VALUES ('Dra. Marina Costa', 'SP-22001', 'Clinica geral',
                'marina@petcare.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                v_b);

        INSERT INTO TB_CLV_COLABORADOR (nm_colaborador, ds_email, ds_senha_hash,
                                        ds_cargo, id_clinica)
        VALUES ('Patricia Moraes', 'patricia@vidaanimal.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                'ADMINISTRADOR', v_a);

        INSERT INTO TB_CLV_COLABORADOR (nm_colaborador, ds_email, ds_senha_hash,
                                        ds_cargo, id_clinica)
        VALUES ('Diego Tavares', 'diego@petcare.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                'RECEPCAO', v_b);

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.BASE', SQLCODE, SQLERRM);
            RAISE;
    END BASE;

    PROCEDURE GERAR (
        p_clinica   IN NUMBER,
        p_pet       IN NUMBER,
        p_consulta  IN NUMBER,
        p_data      IN DATE,
        p_horizonte IN NUMBER DEFAULT 550
    ) IS
        v_qtd NUMBER;
    BEGIN
        PKG_CLV_MOTOR.GERAR_OBRIGACOES(p_clinica, p_pet, p_consulta,
            'CONSULTA_REGISTRADA', NULL, p_data, NULL, p_horizonte, v_qtd);

        PKG_CLV_MOTOR.GERAR_OBRIGACOES(p_clinica, p_pet, p_consulta,
            'PESO_ATUALIZADO', NULL, p_data, NULL, p_horizonte, v_qtd);

        IF PKG_CLV_MOTOR.FASE_VIDA(p_pet, p_data) = 'SENIOR' THEN
            PKG_CLV_MOTOR.GERAR_OBRIGACOES(p_clinica, p_pet, p_consulta,
                'FASE_VIDA_ALTERADA', NULL, p_data, NULL, p_horizonte, v_qtd);
        END IF;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.GERAR', SQLCODE,
                            'pet=' || p_pet || ' consulta=' || p_consulta || ' ' || SQLERRM);
            RAISE;
    END GERAR;

    FUNCTION SEED_CONSULTA (
        p_clinica     IN NUMBER,
        p_pet         IN NUMBER,
        p_vet         IN NUMBER,
        p_data        IN DATE,
        p_motivo      IN VARCHAR2,
        p_peso        IN NUMBER,
        p_valor       IN NUMBER   DEFAULT NULL,
        p_diagnostico IN VARCHAR2 DEFAULT NULL,
        p_gerar       IN CHAR     DEFAULT 'S'
    ) RETURN NUMBER IS
        v_consulta NUMBER(19);
        v_cc       VARCHAR2(12);
        v_sorteio  NUMBER;
    BEGIN
        INSERT INTO TB_CLV_CONSULTA (dt_consulta, ds_motivo, ds_diagnostico,
                                     ds_status, id_pet, id_veterinario, nr_valor)
        VALUES (p_data, p_motivo, p_diagnostico, 'REALIZADA', p_pet, p_vet,
                NVL(p_valor, 180))
        RETURNING id_consulta INTO v_consulta;

        v_sorteio := DBMS_RANDOM.VALUE(0,1);
        v_cc := CASE WHEN v_sorteio < 0.70 THEN 'IDEAL'
                     WHEN v_sorteio < 0.85 THEN 'MAGRO'
                     ELSE 'SOBREPESO' END;

        INSERT INTO TB_CLV_ANAMNESE (id_consulta, ds_queixa_principal, nr_peso_kg,
                                     nr_temperatura_c, nr_freq_cardiaca,
                                     nr_freq_respiratoria, ds_condicao_corporal)
        VALUES (v_consulta, p_motivo, p_peso,
                ROUND(38 + DBMS_RANDOM.VALUE(0, 1.4), 1),
                ROUND(DBMS_RANDOM.VALUE(70, 140)),
                ROUND(DBMS_RANDOM.VALUE(15, 35)),
                v_cc);

        IF p_gerar = 'S' THEN
            GERAR(p_clinica, p_pet, v_consulta, p_data);
        END IF;

        RETURN v_consulta;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.SEED_CONSULTA', SQLCODE,
                            'pet=' || p_pet || ' ' || SQLERRM);
            RAISE;
    END SEED_CONSULTA;

    PROCEDURE POPULACAO (
        p_qtd   IN NUMBER DEFAULT 400,
        p_meses IN NUMBER DEFAULT 18
    ) IS
        TYPE t_txt IS TABLE OF VARCHAR2(60);

        v_nomes  t_txt := t_txt('Luna','Mel','Bidu','Cacau','Amora','Bob','Nina','Zeus',
                                'Frida','Simba','Maia','Pipoca','Rex','Lola','Toby','Bela');
        v_sobre  t_txt := t_txt('Silva','Souza','Oliveira','Santos','Lima','Costa',
                                'Rocha','Martins','Barbosa','Azevedo');
        v_motivo t_txt := t_txt('Consulta de rotina','Vacinacao','Retorno',
                                'Avaliacao clinica','Queixa gastrointestinal',
                                'Avaliacao dermatologica');

        v_clinica_a NUMBER(19);
        v_clinica_b NUMBER(19);
        v_clinica   NUMBER(19);
        v_tutor     NUMBER(19);
        v_pet       NUMBER(19);
        v_vet       NUMBER(19);
        v_raca      NUMBER(19);
        v_porte     VARCHAR2(10);
        v_nasc      DATE;
        v_data      DATE;
        v_peso      NUMBER(7,2);
        v_qtd_c     NUMBER;
        v_cons      NUMBER(19);
    BEGIN
        DBMS_RANDOM.SEED(20260909);   -- reprodutível

        SELECT MIN(id_clinica), MAX(id_clinica)
          INTO v_clinica_a, v_clinica_b FROM TB_CLV_CLINICA;

        FOR i IN 1 .. p_qtd LOOP

            v_clinica := CASE WHEN DBMS_RANDOM.VALUE(0,1) < 0.6
                              THEN v_clinica_a ELSE v_clinica_b END;

            SELECT MIN(id_veterinario) INTO v_vet
              FROM TB_CLV_VETERINARIO WHERE id_clinica = v_clinica;

            -- Raça aleatória; o porte padrão da raça vira o porte do pet.
            SELECT id_raca, NVL(ds_porte_padrao,'MEDIO')
              INTO v_raca, v_porte
              FROM (SELECT id_raca, ds_porte_padrao
                      FROM TB_CLV_RACA
                     WHERE ds_porte_padrao IS NOT NULL
                     ORDER BY DBMS_RANDOM.VALUE)
             WHERE ROWNUM = 1;

            -- Idade entre 2 meses e 12 anos
            v_nasc := TRUNC(SYSDATE) - ROUND(DBMS_RANDOM.VALUE(60, 4380));

            INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                                      ds_canal_preferencial, dt_cadastro, id_clinica)
            VALUES (v_sobre(MOD(i,10)+1) || ' ' || v_sobre(MOD(i*3,10)+1),
                    'tutor' || i || '@exemplo.com',
                    '119' || LPAD(i, 8, '0'),
                    '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                    CASE WHEN MOD(i,4) = 0 THEN 'WEB' ELSE 'APP' END,
                    TRUNC(SYSDATE) - ROUND(DBMS_RANDOM.VALUE(30, 900)),
                    v_clinica)
            RETURNING id_tutor INTO v_tutor;

            INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip,
                                    ds_porte, fl_castrado, ds_status_pet,
                                    id_tutor, id_raca, id_clinica)
            VALUES (v_nomes(MOD(i,16)+1), v_nasc,
                    CASE WHEN MOD(i,2) = 0 THEN 'M' ELSE 'F' END,
                    'CHIP' || LPAD(i+1000, 12, '0'),
                    v_porte,
                    CASE WHEN DBMS_RANDOM.VALUE(0,1) < 0.55 THEN 'S' ELSE 'N' END,
                    'ATIVO', v_tutor, v_raca, v_clinica)
            RETURNING id_pet INTO v_pet;

            -- Peso base coerente com o porte
            v_peso := CASE v_porte
                          WHEN 'MINI'    THEN DBMS_RANDOM.VALUE(2, 5)
                          WHEN 'PEQUENO' THEN DBMS_RANDOM.VALUE(5, 10)
                          WHEN 'MEDIO'   THEN DBMS_RANDOM.VALUE(10, 25)
                          WHEN 'GRANDE'  THEN DBMS_RANDOM.VALUE(25, 40)
                          ELSE                DBMS_RANDOM.VALUE(40, 65)
                      END;

            -- 2 a 6 consultas na janela histórica, sempre após o nascimento
            v_qtd_c := 2 + ROUND(DBMS_RANDOM.VALUE(0, 4));
            FOR j IN 1 .. v_qtd_c LOOP
                v_data := TRUNC(SYSDATE) - ROUND(DBMS_RANDOM.VALUE(5, p_meses*30));
                IF v_data > v_nasc + 30 THEN
                    v_cons := SEED_CONSULTA(
                        v_clinica, v_pet, v_vet, v_data,
                        v_motivo(MOD(i+j,6)+1),
                        ROUND(v_peso * DBMS_RANDOM.VALUE(0.92, 1.08), 2),
                        ROUND(DBMS_RANDOM.VALUE(120, 520)));
                END IF;
            END LOOP;

            IF MOD(i, 50) = 0 THEN
                COMMIT;
            END IF;
        END LOOP;

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.POPULACAO', SQLCODE, SQLERRM);
            RAISE;
    END POPULACAO;

    PROCEDURE HEROIS IS
        v_clinica NUMBER(19);
        v_vet     NUMBER(19);
        v_vet_nf  NUMBER(19);
        v_tutor   NUMBER(19);
        v_pet     NUMBER(19);
        v_raca    NUMBER(19);
        v_cons    NUMBER(19);
        v_cond    NUMBER(19);
        v_qtd     NUMBER;
    BEGIN
        SELECT MIN(id_clinica) INTO v_clinica FROM TB_CLV_CLINICA;
        SELECT MIN(id_veterinario), MAX(id_veterinario) INTO v_vet, v_vet_nf
          FROM TB_CLV_VETERINARIO WHERE id_clinica = v_clinica;

        -- ---------- THOR · Golden Retriever, ~3 meses ----------
        INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                                  ds_canal_preferencial, dt_cadastro, id_clinica)
        VALUES ('Camila Ferreira', 'camila.ferreira@exemplo.com', '11987650001',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                'APP', SYSDATE-70, v_clinica)
        RETURNING id_tutor INTO v_tutor;

        SELECT id_raca INTO v_raca FROM TB_CLV_RACA WHERE nm_raca = 'Golden Retriever';

        INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip,
                                ds_pelagem, ds_porte, fl_castrado, ds_status_pet,
                                id_tutor, id_raca, id_clinica)
        VALUES ('Thor', TRUNC(SYSDATE)-95, 'M', 'CHIP900000000001',
                'Dourada', 'GRANDE', 'N', 'ATIVO', v_tutor, v_raca, v_clinica)
        RETURNING id_pet INTO v_pet;

        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet, TRUNC(SYSDATE)-50,
                                'Primeira consulta de filhote', 6.2, 180);
        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet, TRUNC(SYSDATE)-20,
                                'Retorno e segunda dose', 9.8, 120);

        -- ---------- NALA · Persa, ~8 anos, DRC ----------
        INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                                  ds_canal_preferencial, dt_cadastro, id_clinica)
        VALUES ('Roberto Almeida', 'roberto.almeida@exemplo.com', '11987650002',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                'APP', SYSDATE-700, v_clinica)
        RETURNING id_tutor INTO v_tutor;

        SELECT id_raca INTO v_raca FROM TB_CLV_RACA WHERE nm_raca = 'Persa';

        INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip,
                                ds_pelagem, ds_porte, fl_castrado, ds_status_pet,
                                id_tutor, id_raca, id_clinica)
        VALUES ('Nala', ADD_MONTHS(TRUNC(SYSDATE), -98), 'F', 'CHIP900000000002',
                'Creme', 'PEQUENO', 'S', 'EM_TRATAMENTO', v_tutor, v_raca, v_clinica)
        RETURNING id_pet INTO v_pet;

        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet,
                                ADD_MONTHS(TRUNC(SYSDATE),-16),
                                'Check-up anual', 4.1, 260);
        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet,
                                ADD_MONTHS(TRUNC(SYSDATE),-10),
                                'Apatia e perda de peso', 3.6, 320);
        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet_nf,
                                ADD_MONTHS(TRUNC(SYSDATE),-6),
                                'Investigacao renal', 3.4, 420,
                                'Doenca renal cronica estagio 2');

        -- Diagnóstico crônico ativa o protocolo de monitoramento renal.
        SELECT id_tipo_condicao INTO v_cond
          FROM TB_CLV_TIPO_CONDICAO WHERE nm_condicao = 'Doenca renal cronica';

        INSERT INTO TB_CLV_CONDICAO_PET (id_pet, id_tipo_condicao, dt_diagnostico,
                                         ds_observacao, fl_ativo, id_consulta_origem)
        VALUES (v_pet, v_cond, ADD_MONTHS(TRUNC(SYSDATE),-6),
                'Estagio 2 IRIS. Dieta renal iniciada.', 'S', v_cons);

        PKG_CLV_MOTOR.GERAR_OBRIGACOES(v_clinica, v_pet, v_cons,
            'CONDICAO_DIAGNOSTICADA', NULL,
            ADD_MONTHS(TRUNC(SYSDATE),-6), NULL, 400, v_qtd);

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.HEROIS', SQLCODE, SQLERRM);
            RAISE;
    END HEROIS;

    PROCEDURE DESFECHOS (
        p_taxa_tratado  IN NUMBER DEFAULT 0.58,
        p_taxa_controle IN NUMBER DEFAULT 0.36
    ) IS
        v_taxa    NUMBER;
        v_sorteio NUMBER;
        v_cons    NUMBER(19);
        v_agend   NUMBER(19);
        v_vet     NUMBER(19);
        v_i       NUMBER := 0;
    BEGIN
        DBMS_RANDOM.SEED(20260910);

        FOR o IN (SELECT o.id_obrigacao, o.id_clinica, o.id_pet, o.fl_grupo_controle,
                         o.dt_prevista, o.nr_valor_estimado, e.nm_etapa
                    FROM TB_CLV_OBRIGACAO o
                    JOIN TB_CLV_ETAPA_PROTOCOLO e ON e.id_etapa = o.id_etapa
                   WHERE o.ds_status = 'PREVISTA'
                   ORDER BY o.dt_prevista) LOOP

            SELECT MIN(id_veterinario) INTO v_vet
              FROM TB_CLV_VETERINARIO WHERE id_clinica = o.id_clinica;

            IF o.dt_prevista < TRUNC(SYSDATE) THEN

                IF o.fl_grupo_controle = 'N' THEN
                    PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'NOTIFICADA',
                        'Lembrete enviado', 'SEED');
                    v_taxa := p_taxa_tratado;
                ELSE
                    v_taxa := p_taxa_controle;
                END IF;

                v_sorteio := DBMS_RANDOM.VALUE(0,1);

                IF v_sorteio < v_taxa THEN
                    INSERT INTO TB_CLV_AGENDAMENTO (dt_agendamento, hr_agendamento,
                            ds_status, ds_canal_origem, id_pet, id_veterinario)
                    VALUES (o.dt_prevista, '09:00', 'REALIZADO',
                            CASE WHEN o.fl_grupo_controle = 'N' THEN 'APP' ELSE 'WEB' END,
                            o.id_pet, v_vet)
                    RETURNING id_agendamento INTO v_agend;

                    PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'AGENDADA',
                        'Tutor agendou', 'SEED', NULL, v_agend);

                    INSERT INTO TB_CLV_CONSULTA (dt_consulta, ds_motivo, ds_status,
                                                 id_pet, id_veterinario, nr_valor)
                    VALUES (o.dt_prevista, SUBSTR(o.nm_etapa,1,500), 'REALIZADA',
                            o.id_pet, v_vet, o.nr_valor_estimado)
                    RETURNING id_consulta INTO v_cons;

                    UPDATE TB_CLV_AGENDAMENTO SET id_consulta = v_cons
                     WHERE id_agendamento = v_agend;

                    PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'CUMPRIDA',
                        'Comparecimento confirmado', 'SEED', NULL, NULL, v_cons,
                        o.nr_valor_estimado);
                ELSE
                    PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'PERDIDA',
                        'Janela encerrada sem retorno', 'SEED');
                END IF;

            ELSIF o.dt_prevista <= TRUNC(SYSDATE) + 30
                  AND o.fl_grupo_controle = 'N' THEN
                PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'NOTIFICADA',
                    'Lembrete enviado', 'SEED');
            END IF;

            v_i := v_i + 1;
            IF MOD(v_i, 200) = 0 THEN
                COMMIT;
            END IF;
        END LOOP;

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.DESFECHOS', SQLCODE, SQLERRM);
            RAISE;
    END DESFECHOS;

    PROCEDURE EXECUTAR (
        p_qtd   IN NUMBER DEFAULT 400,
        p_meses IN NUMBER DEFAULT 18
    ) IS
    BEGIN
        LIMPAR;                 DBMS_OUTPUT.PUT_LINE('1/5 limpeza ok');
        BASE;                   DBMS_OUTPUT.PUT_LINE('2/5 clinicas ok');
        HEROIS;                 DBMS_OUTPUT.PUT_LINE('3/5 Thor e Nala ok');
        POPULACAO(p_qtd, p_meses);
                                DBMS_OUTPUT.PUT_LINE('4/5 populacao ok');
        DESFECHOS;              DBMS_OUTPUT.PUT_LINE('5/5 desfechos ok');
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.EXECUTAR', SQLCODE,
                            'qtd=' || p_qtd || ' meses=' || p_meses || ' ' || SQLERRM);
            RAISE;
    END EXECUTAR;

END PKG_CLV_SEED;
/


-- ------------------------------------------------------------
-- 3. Os quatro pacotes compilaram? (antes de tocar no que está em uso)
-- ------------------------------------------------------------
-- Até aqui só existem objetos novos; o trigger e as rotinas soltas estão
-- intactos. Abortar neste ponto deixa o banco funcionando como antes.
DECLARE
    v_qtd   NUMBER;
    v_lista VARCHAR2(4000);
BEGIN
    SELECT COUNT(*)
      INTO v_qtd
      FROM USER_OBJECTS
     WHERE object_name IN ('PKG_CLV_LOG','PKG_CLV_MOTOR','PKG_CLV_AUDITORIA','PKG_CLV_SEED')
       AND object_type IN ('PACKAGE','PACKAGE BODY')
       AND status = 'VALID';

    IF v_qtd <> 8 THEN
        SELECT LISTAGG(o.object_type || ' ' || o.object_name || ' ' || o.status, ', ')
                 WITHIN GROUP (ORDER BY o.object_name, o.object_type)
          INTO v_lista
          FROM USER_OBJECTS o
         WHERE o.object_name LIKE 'PKG\_CLV\_%' ESCAPE '\';

        RAISE_APPLICATION_ERROR(-20901,
            'V15 abortada antes do trigger e dos DROP: ' || v_qtd
            || ' de 8 specs/bodies VALID. Estado: ' || NVL(v_lista, 'nenhum pacote')
            || '. Ver USER_ERRORS.');
    END IF;
END;
/


-- ------------------------------------------------------------
-- 4. Trigger: monta o JSON e delega
-- ------------------------------------------------------------
-- O JSON de v_old/v_new é o mesmo da V7, linha por linha: ele entra no hash.

CREATE OR REPLACE EDITIONABLE TRIGGER TR_CLV_AUDIT_OBRIGACAO
AFTER INSERT OR UPDATE OR DELETE ON TB_CLV_OBRIGACAO
FOR EACH ROW
DECLARE
    v_op       VARCHAR2(10);
    v_old      VARCHAR2(4000);
    v_new      VARCHAR2(4000);
    v_id_obr   NUMBER(19);
    v_clinica  NUMBER(19);
BEGIN
    IF INSERTING THEN
        v_op := 'INSERT';
    ELSIF UPDATING THEN
        v_op := 'UPDATE';
    ELSE
        v_op := 'DELETE';
    END IF;

    v_id_obr  := NVL(:NEW.id_obrigacao, :OLD.id_obrigacao);
    v_clinica := NVL(:NEW.id_clinica,   :OLD.id_clinica);

    IF NOT INSERTING THEN
        v_old := '{"status":"'      || :OLD.ds_status          ||
                 '","grupoCtrl":"'  || :OLD.fl_grupo_controle  ||
                 '","dtPrevista":"' || TO_CHAR(:OLD.dt_prevista,'YYYY-MM-DD') ||
                 '","valorReal":'   || NVL(TO_CHAR(:OLD.nr_valor_realizado),'null') || '}';
    END IF;

    IF NOT DELETING THEN
        v_new := '{"status":"'      || :NEW.ds_status          ||
                 '","grupoCtrl":"'  || :NEW.fl_grupo_controle  ||
                 '","dtPrevista":"' || TO_CHAR(:NEW.dt_prevista,'YYYY-MM-DD') ||
                 '","valorReal":'   || NVL(TO_CHAR(:NEW.nr_valor_realizado),'null') || '}';
    END IF;

    PKG_CLV_AUDITORIA.REGISTRAR_TRANSICAO(v_op, v_id_obr, v_clinica, v_old, v_new);
END TR_CLV_AUDIT_OBRIGACAO;
/

ALTER TRIGGER TR_CLV_AUDIT_OBRIGACAO ENABLE;


-- ------------------------------------------------------------
-- 5. DROP das rotinas soltas
-- ------------------------------------------------------------
-- Sem wrapper com o nome antigo: quem ainda chamar PR_CLV_/FN_CLV_ tem que
-- quebrar, não seguir funcionando por um caminho paralelo. O laço pula o que
-- já não existe, para que uma reexecução depois de falha não pare aqui.
DECLARE
    TYPE t_nomes IS TABLE OF VARCHAR2(30);
    v_nomes t_nomes := t_nomes(
        'PR_CLV_SEED_EXECUTAR','PR_CLV_SEED_DESFECHOS','PR_CLV_SEED_HEROIS',
        'PR_CLV_SEED_POPULACAO','PR_CLV_SEED_BASE','PR_CLV_SEED_LIMPAR',
        'FN_CLV_SEED_CONSULTA','PR_CLV_SEED_GERAR',
        'PR_CLV_TRANSITAR_OBRIGACAO','PR_CLV_GERAR_OBRIGACOES',
        'FN_CLV_VERIFICA_CADEIA','FN_CLV_AVALIA_REGRA','FN_CLV_AVALIA_CONDICAO',
        'FN_CLV_GRUPO_CONTROLE','FN_CLV_CALCULAR_DATA','FN_CLV_FASE_VIDA',
        'FN_CLV_PESO_ATUAL','FN_CLV_IDADE_MESES',
        'PR_CLV_LOG_ERRO');
BEGIN
    FOR i IN 1 .. v_nomes.COUNT LOOP
        FOR o IN (SELECT object_type, object_name
                    FROM USER_OBJECTS
                   WHERE object_name = v_nomes(i)
                     AND object_type IN ('PROCEDURE','FUNCTION')) LOOP
            EXECUTE IMMEDIATE 'DROP ' || o.object_type || ' ' || o.object_name;
        END LOOP;
    END LOOP;
END;
/


-- ------------------------------------------------------------
-- 6. Conferência final
-- ------------------------------------------------------------
DECLARE
    v_invalidos VARCHAR2(4000);
    v_sobras    VARCHAR2(4000);
BEGIN
    SELECT LISTAGG(object_type || ' ' || object_name, ', ')
             WITHIN GROUP (ORDER BY object_name, object_type)
      INTO v_invalidos
      FROM USER_OBJECTS
     WHERE (object_name LIKE 'PKG\_CLV\_%' ESCAPE '\'
            OR object_name = 'TR_CLV_AUDIT_OBRIGACAO')
       AND status <> 'VALID';

    SELECT LISTAGG(object_type || ' ' || object_name, ', ')
             WITHIN GROUP (ORDER BY object_name)
      INTO v_sobras
      FROM USER_OBJECTS
     WHERE object_type IN ('PROCEDURE','FUNCTION')
       AND object_name IN (
             'PR_CLV_LOG_ERRO',
             'FN_CLV_IDADE_MESES','FN_CLV_PESO_ATUAL','FN_CLV_FASE_VIDA',
             'FN_CLV_CALCULAR_DATA','FN_CLV_GRUPO_CONTROLE',
             'FN_CLV_AVALIA_CONDICAO','FN_CLV_AVALIA_REGRA',
             'FN_CLV_VERIFICA_CADEIA',
             'PR_CLV_GERAR_OBRIGACOES','PR_CLV_TRANSITAR_OBRIGACAO',
             'FN_CLV_SEED_CONSULTA','PR_CLV_SEED_LIMPAR','PR_CLV_SEED_BASE',
             'PR_CLV_SEED_POPULACAO','PR_CLV_SEED_GERAR','PR_CLV_SEED_DESFECHOS',
             'PR_CLV_SEED_HEROIS','PR_CLV_SEED_EXECUTAR');

    IF v_invalidos IS NOT NULL OR v_sobras IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20902,
            'V15 terminou inconsistente. INVALID: ' || NVL(v_invalidos, '-')
            || ' | rotinas soltas restantes: ' || NVL(v_sobras, '-'));
    END IF;
END;
/
