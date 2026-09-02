-- ============================================================
-- MIGRATION V7 - Motor de protocolo
-- ============================================================
-- Cria o motor que decide quando gerar obrigacao e qual transicao e legal:
-- catalogo de protocolo, obrigacoes, trilha de auditoria, outbox e a view do
-- painel de receita. A aplicacao chama as procedures daqui; nunca reimplementa
-- a regra em Java.
--
-- Extraido do schema da FIAP, onde estes objetos ja existiam aplicados a mao.
-- La nada disto executa: o Flyway esta com baseline em 8. Num banco limpo
-- (o container do docker-compose) e este arquivo que cria o motor inteiro.
--
-- Ordem: tabelas -> sequence -> FKs -> indices -> PR_CLV_LOG_ERRO -> funcoes
-- -> procedures do motor -> trigger -> view. O log de erro vem antes das
-- funcoes porque todas o chamam no handler de excecao.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Tabelas (sem foreign key: elas vem no bloco 3)
-- ------------------------------------------------------------

CREATE TABLE TB_CLV_PROTOCOLO 
   (	ID_PROTOCOLO NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	ID_CLINICA NUMBER(19,0), 
	DS_CODIGO VARCHAR2(60) NOT NULL ENABLE, 
	NM_PROTOCOLO VARCHAR2(160) NOT NULL ENABLE, 
	DS_ESPECIE VARCHAR2(10) NOT NULL ENABLE, 
	DS_CATEGORIA VARCHAR2(20) NOT NULL ENABLE, 
	DS_DESCRICAO VARCHAR2(600), 
	FL_ATIVO CHAR(1) DEFAULT 'S' NOT NULL ENABLE, 
	DT_CRIACAO TIMESTAMP (6) DEFAULT SYSTIMESTAMP NOT NULL ENABLE, 
	 CONSTRAINT CHK_PROTOCOLO_ESP CHECK (ds_especie IN ('CANINA','FELINA')) ENABLE, 
	 CONSTRAINT CHK_PROTOCOLO_CAT CHECK (ds_categoria IN
        ('VACINA','VERMIFUGO','CIRURGIA','EXAME','ODONTO','CHECKUP','MONITORAMENTO','RETORNO')) ENABLE, 
	 CONSTRAINT CHK_PROTOCOLO_ATIVO CHECK (fl_ativo IN ('S','N')) ENABLE, 
	 PRIMARY KEY (ID_PROTOCOLO)
  USING INDEX  ENABLE, 
	 CONSTRAINT UK_PROTOCOLO_CODIGO UNIQUE (DS_CODIGO)
  USING INDEX  ENABLE
   ) ;

CREATE TABLE TB_CLV_VERSAO_PROTOCOLO 
   (	ID_VERSAO_PROTOCOLO NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	ID_PROTOCOLO NUMBER(19,0) NOT NULL ENABLE, 
	NR_VERSAO NUMBER(4,0) NOT NULL ENABLE, 
	DS_STATUS VARCHAR2(12) DEFAULT 'VIGENTE' NOT NULL ENABLE, 
	DT_VIGENTE_DE DATE DEFAULT TRUNC(SYSDATE) NOT NULL ENABLE, 
	DT_VIGENTE_ATE DATE, 
	DS_FONTE_CLINICA VARCHAR2(300), 
	DS_NOTAS VARCHAR2(600), 
	 CONSTRAINT CHK_VERSAO_STATUS CHECK (ds_status IN ('RASCUNHO','VIGENTE','APOSENTADA')) ENABLE, 
	 PRIMARY KEY (ID_VERSAO_PROTOCOLO)
  USING INDEX  ENABLE, 
	 CONSTRAINT UK_VERSAO_PROTOCOLO UNIQUE (ID_PROTOCOLO, NR_VERSAO)
  USING INDEX  ENABLE
   ) ;

CREATE TABLE TB_CLV_ETAPA_PROTOCOLO 
   (	ID_ETAPA NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	ID_VERSAO_PROTOCOLO NUMBER(19,0) NOT NULL ENABLE, 
	NR_ORDEM NUMBER(3,0) NOT NULL ENABLE, 
	NM_ETAPA VARCHAR2(160) NOT NULL ENABLE, 
	DS_TIPO_INTERVALO VARCHAR2(20) NOT NULL ENABLE, 
	ID_ETAPA_REFERENCIA NUMBER(19,0), 
	NR_OFFSET_DIAS NUMBER(6,0) DEFAULT 0 NOT NULL ENABLE, 
	NR_JANELA_ANTES NUMBER(4,0) DEFAULT 7 NOT NULL ENABLE, 
	NR_JANELA_DEPOIS NUMBER(4,0) DEFAULT 30 NOT NULL ENABLE, 
	NR_RECORRENCIA_DIAS NUMBER(6,0), 
	DS_PROCEDIMENTO VARCHAR2(60), 
	ID_TIPO_VACINA NUMBER(19,0), 
	NR_VALOR_REFERENCIA NUMBER(10,2), 
	NR_MAX_OCORRENCIAS NUMBER(3,0), 
	 CONSTRAINT CHK_ETAPA_TIPO CHECK (ds_tipo_intervalo IN
        ('APOS_REFERENCIA','APOS_ETAPA','RECORRENTE')) ENABLE, 
	 PRIMARY KEY (ID_ETAPA)
  USING INDEX  ENABLE, 
	 CONSTRAINT UK_ETAPA_ORDEM UNIQUE (ID_VERSAO_PROTOCOLO, NR_ORDEM)
  USING INDEX  ENABLE
   ) ;

CREATE TABLE TB_CLV_REGRA_ATIVACAO 
   (	ID_REGRA_ATIVACAO NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	ID_VERSAO_PROTOCOLO NUMBER(19,0) NOT NULL ENABLE, 
	DS_EVENTO_GATILHO VARCHAR2(30) NOT NULL ENABLE, 
	DS_OPERADOR_LOGICO VARCHAR2(3) DEFAULT 'AND' NOT NULL ENABLE, 
	NR_PRIORIDADE NUMBER(3,0) DEFAULT 50 NOT NULL ENABLE, 
	 CONSTRAINT CHK_REGRA_GATILHO CHECK (ds_evento_gatilho IN
        ('CONSULTA_REGISTRADA','CONDICAO_DIAGNOSTICADA','FASE_VIDA_ALTERADA',
         'PESO_ATUALIZADO','VACINA_APLICADA','PROCEDIMENTO_REALIZADO','PET_CADASTRADO')) ENABLE, 
	 CONSTRAINT CHK_REGRA_OPERADOR CHECK (ds_operador_logico IN ('AND','OR')) ENABLE, 
	 PRIMARY KEY (ID_REGRA_ATIVACAO)
  USING INDEX  ENABLE
   ) ;

CREATE TABLE TB_CLV_CONDICAO_PROT 
   (	ID_CONDICAO_PROT NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	ID_REGRA_ATIVACAO NUMBER(19,0) NOT NULL ENABLE, 
	DS_ATRIBUTO VARCHAR2(20) NOT NULL ENABLE, 
	DS_OPERADOR VARCHAR2(10) NOT NULL ENABLE, 
	DS_VALOR_TEXTO VARCHAR2(400), 
	NR_VALOR_MIN NUMBER(10,2), 
	NR_VALOR_MAX NUMBER(10,2), 
	FL_POR_PORTE CHAR(1) DEFAULT 'N' NOT NULL ENABLE, 
	 CONSTRAINT CHK_COND_ATRIBUTO CHECK (ds_atributo IN
        ('ESPECIE','RACA','GRUPO_RACA','PORTE','BRAQUICEFALICO','IDADE_MESES',
         'PESO_KG','CONDICAO','PROCEDIMENTO','SEXO','CASTRADO')) ENABLE, 
	 CONSTRAINT CHK_COND_OPERADOR CHECK (ds_operador IN
        ('EQ','NEQ','IN','NOT_IN','BETWEEN','GTE','LTE')) ENABLE, 
	 CONSTRAINT CHK_COND_POR_PORTE CHECK (fl_por_porte IN ('S','N')) ENABLE, 
	 PRIMARY KEY (ID_CONDICAO_PROT)
  USING INDEX  ENABLE
   ) ;

CREATE TABLE TB_CLV_PARAM_PORTE 
   (	ID_PARAM_PORTE NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	ID_CONDICAO_PROT NUMBER(19,0) NOT NULL ENABLE, 
	DS_PORTE VARCHAR2(10) NOT NULL ENABLE, 
	NR_VALOR_MIN NUMBER(10,2), 
	NR_VALOR_MAX NUMBER(10,2), 
	 CONSTRAINT CHK_PARAM_PORTE CHECK (ds_porte IN ('MINI','PEQUENO','MEDIO','GRANDE','GIGANTE')) ENABLE, 
	 PRIMARY KEY (ID_PARAM_PORTE)
  USING INDEX  ENABLE, 
	 CONSTRAINT UK_PARAM_PORTE UNIQUE (ID_CONDICAO_PROT, DS_PORTE)
  USING INDEX  ENABLE
   ) ;

CREATE TABLE TB_CLV_OBRIGACAO 
   (	ID_OBRIGACAO NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	ID_CLINICA NUMBER(19,0) NOT NULL ENABLE, 
	ID_PET NUMBER(19,0) NOT NULL ENABLE, 
	ID_VERSAO_PROTOCOLO NUMBER(19,0) NOT NULL ENABLE, 
	ID_ETAPA NUMBER(19,0) NOT NULL ENABLE, 
	ID_CONSULTA_ORIGEM NUMBER(19,0), 
	DT_PREVISTA DATE NOT NULL ENABLE, 
	DT_JANELA_INICIO DATE NOT NULL ENABLE, 
	DT_JANELA_FIM DATE NOT NULL ENABLE, 
	DS_STATUS VARCHAR2(12) DEFAULT 'PREVISTA' NOT NULL ENABLE, 
	DS_CORRELATION_ID VARCHAR2(36) NOT NULL ENABLE, 
	DS_CAUSATION_ID VARCHAR2(36), 
	FL_GRUPO_CONTROLE CHAR(1) DEFAULT 'N' NOT NULL ENABLE, 
	DS_SORTEIO_SEED VARCHAR2(200), 
	DS_HASH_SORTEIO VARCHAR2(64), 
	NR_VERSAO_SORTEIO NUMBER(3,0) DEFAULT 1, 
	ID_AGENDAMENTO NUMBER(19,0), 
	ID_CONSULTA_DESTINO NUMBER(19,0), 
	NR_VALOR_ESTIMADO NUMBER(10,2), 
	NR_VALOR_REALIZADO NUMBER(10,2), 
	DT_NOTIFICADA TIMESTAMP (6), 
	DT_RESPONDIDA TIMESTAMP (6), 
	DT_AGENDADA TIMESTAMP (6), 
	DT_CUMPRIDA TIMESTAMP (6), 
	DT_CRIACAO TIMESTAMP (6) DEFAULT SYSTIMESTAMP NOT NULL ENABLE, 
	DT_ATUALIZACAO TIMESTAMP (6) DEFAULT SYSTIMESTAMP NOT NULL ENABLE, 
	 CONSTRAINT CHK_OBRIGACAO_STATUS CHECK (ds_status IN
        ('PREVISTA','NOTIFICADA','RESPONDIDA','AGENDADA','CUMPRIDA','PERDIDA','CANCELADA')) ENABLE, 
	 CONSTRAINT CHK_OBRIGACAO_CTRL CHECK (fl_grupo_controle IN ('S','N')) ENABLE, 
	 CONSTRAINT CHK_OBRIGACAO_JANELA CHECK (dt_janela_fim >= dt_janela_inicio) ENABLE, 
	 PRIMARY KEY (ID_OBRIGACAO)
  USING INDEX  ENABLE
   ) ;

CREATE TABLE TB_CLV_OBRIGACAO_TRANSICAO 
   (	ID_TRANSICAO NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	ID_CLINICA NUMBER(19,0) NOT NULL ENABLE, 
	ID_OBRIGACAO NUMBER(19,0) NOT NULL ENABLE, 
	DS_STATUS_DE VARCHAR2(12), 
	DS_STATUS_PARA VARCHAR2(12) NOT NULL ENABLE, 
	DS_MOTIVO VARCHAR2(200), 
	DS_CAUSATION_ID VARCHAR2(36), 
	NM_USUARIO VARCHAR2(80), 
	DT_OCORRENCIA TIMESTAMP (6) DEFAULT SYSTIMESTAMP NOT NULL ENABLE, 
	 PRIMARY KEY (ID_TRANSICAO)
  USING INDEX  ENABLE
   ) ;

CREATE TABLE TB_CLV_AUDIT_OBRIGACAO 
   (	ID_AUDIT NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	NR_SEQ NUMBER(19,0) NOT NULL ENABLE, 
	ID_CLINICA NUMBER(19,0) NOT NULL ENABLE, 
	ID_OBRIGACAO NUMBER(19,0) NOT NULL ENABLE, 
	DS_OPERACAO VARCHAR2(10) NOT NULL ENABLE, 
	DS_VALOR_OLD CLOB, 
	DS_VALOR_NEW CLOB, 
	NM_USUARIO VARCHAR2(80), 
	DT_OCORRENCIA TIMESTAMP (6) DEFAULT SYSTIMESTAMP NOT NULL ENABLE, 
	DS_HASH_ANTERIOR VARCHAR2(64), 
	DS_HASH_ATUAL VARCHAR2(64) NOT NULL ENABLE, 
	 CONSTRAINT CHK_AUDIT_OPER CHECK (ds_operacao IN ('INSERT','UPDATE','DELETE')) ENABLE, 
	 PRIMARY KEY (ID_AUDIT)
  USING INDEX  ENABLE, 
	 CONSTRAINT UK_AUDIT_SEQ UNIQUE (NR_SEQ)
  USING INDEX  ENABLE
   ) ;

CREATE TABLE TB_CLV_OUTBOX_EVENT 
   (	ID_EVENTO NUMBER(19,0) GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE  NOKEEP  NOSCALE  NOT NULL ENABLE, 
	DS_EVENT_UUID VARCHAR2(36) NOT NULL ENABLE, 
	DS_EVENT_TYPE VARCHAR2(80) NOT NULL ENABLE, 
	NR_EVENT_VERSION NUMBER(3,0) DEFAULT 1 NOT NULL ENABLE, 
	DS_CORRELATION_ID VARCHAR2(36) NOT NULL ENABLE, 
	DS_CAUSATION_ID VARCHAR2(36), 
	ID_CLINICA NUMBER(19,0) NOT NULL ENABLE, 
	NM_USUARIO VARCHAR2(80), 
	DS_AGREGADO VARCHAR2(40) NOT NULL ENABLE, 
	NR_ID_AGREGADO NUMBER(19,0) NOT NULL ENABLE, 
	DS_PAYLOAD CLOB NOT NULL ENABLE, 
	DT_OCORRENCIA TIMESTAMP (6) DEFAULT SYSTIMESTAMP NOT NULL ENABLE, 
	DS_STATUS VARCHAR2(12) DEFAULT 'PENDENTE' NOT NULL ENABLE, 
	NR_TENTATIVAS NUMBER(3,0) DEFAULT 0 NOT NULL ENABLE, 
	DT_PUBLICACAO TIMESTAMP (6), 
	DS_ULTIMO_ERRO VARCHAR2(600), 
	 CONSTRAINT CHK_OUTBOX_STATUS CHECK (ds_status IN ('PENDENTE','PUBLICADO','FALHA')) ENABLE, 
	 PRIMARY KEY (ID_EVENTO)
  USING INDEX  ENABLE, 
	 CONSTRAINT UK_OUTBOX_UUID UNIQUE (DS_EVENT_UUID)
  USING INDEX  ENABLE
   ) ;


-- ------------------------------------------------------------
-- 2. Sequence da trilha de auditoria
-- ------------------------------------------------------------

CREATE SEQUENCE  SQ_CLV_AUDIT_OBRIGACAO  MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 NOCACHE  ORDER  NOCYCLE  NOKEEP  NOSCALE  GLOBAL ;

-- ------------------------------------------------------------
-- 3. Foreign keys
-- ------------------------------------------------------------

ALTER TABLE TB_CLV_PROTOCOLO ADD CONSTRAINT FK_PROTOCOLO_CLINICA FOREIGN KEY (ID_CLINICA)
	  REFERENCES TB_CLV_CLINICA (ID_CLINICA) ENABLE;

ALTER TABLE TB_CLV_VERSAO_PROTOCOLO ADD CONSTRAINT FK_VERSAO_PROTOCOLO FOREIGN KEY (ID_PROTOCOLO)
	  REFERENCES TB_CLV_PROTOCOLO (ID_PROTOCOLO) ENABLE;

ALTER TABLE TB_CLV_ETAPA_PROTOCOLO ADD CONSTRAINT FK_ETAPA_REFERENCIA FOREIGN KEY (ID_ETAPA_REFERENCIA)
	  REFERENCES TB_CLV_ETAPA_PROTOCOLO (ID_ETAPA) ENABLE;
  ALTER TABLE TB_CLV_ETAPA_PROTOCOLO ADD CONSTRAINT FK_ETAPA_TIPO_VACINA FOREIGN KEY (ID_TIPO_VACINA)
	  REFERENCES TB_CLV_TIPO_VACINA (ID_TIPO_VACINA) ENABLE;
  ALTER TABLE TB_CLV_ETAPA_PROTOCOLO ADD CONSTRAINT FK_ETAPA_VERSAO FOREIGN KEY (ID_VERSAO_PROTOCOLO)
	  REFERENCES TB_CLV_VERSAO_PROTOCOLO (ID_VERSAO_PROTOCOLO) ENABLE;

ALTER TABLE TB_CLV_REGRA_ATIVACAO ADD CONSTRAINT FK_REGRA_VERSAO FOREIGN KEY (ID_VERSAO_PROTOCOLO)
	  REFERENCES TB_CLV_VERSAO_PROTOCOLO (ID_VERSAO_PROTOCOLO) ENABLE;

ALTER TABLE TB_CLV_CONDICAO_PROT ADD CONSTRAINT FK_COND_REGRA FOREIGN KEY (ID_REGRA_ATIVACAO)
	  REFERENCES TB_CLV_REGRA_ATIVACAO (ID_REGRA_ATIVACAO) ENABLE;

ALTER TABLE TB_CLV_PARAM_PORTE ADD CONSTRAINT FK_PARAM_CONDICAO FOREIGN KEY (ID_CONDICAO_PROT)
	  REFERENCES TB_CLV_CONDICAO_PROT (ID_CONDICAO_PROT) ENABLE;

ALTER TABLE TB_CLV_OBRIGACAO ADD CONSTRAINT FK_OBRIGACAO_AGEND FOREIGN KEY (ID_AGENDAMENTO)
	  REFERENCES TB_CLV_AGENDAMENTO (ID_AGENDAMENTO) ENABLE;
  ALTER TABLE TB_CLV_OBRIGACAO ADD CONSTRAINT FK_OBRIGACAO_CLINICA FOREIGN KEY (ID_CLINICA)
	  REFERENCES TB_CLV_CLINICA (ID_CLINICA) ENABLE;
  ALTER TABLE TB_CLV_OBRIGACAO ADD CONSTRAINT FK_OBRIGACAO_DESTINO FOREIGN KEY (ID_CONSULTA_DESTINO)
	  REFERENCES TB_CLV_CONSULTA (ID_CONSULTA) ENABLE;
  ALTER TABLE TB_CLV_OBRIGACAO ADD CONSTRAINT FK_OBRIGACAO_ETAPA FOREIGN KEY (ID_ETAPA)
	  REFERENCES TB_CLV_ETAPA_PROTOCOLO (ID_ETAPA) ENABLE;
  ALTER TABLE TB_CLV_OBRIGACAO ADD CONSTRAINT FK_OBRIGACAO_ORIGEM FOREIGN KEY (ID_CONSULTA_ORIGEM)
	  REFERENCES TB_CLV_CONSULTA (ID_CONSULTA) ENABLE;
  ALTER TABLE TB_CLV_OBRIGACAO ADD CONSTRAINT FK_OBRIGACAO_PET FOREIGN KEY (ID_PET)
	  REFERENCES TB_CLV_PET (ID_PET) ENABLE;
  ALTER TABLE TB_CLV_OBRIGACAO ADD CONSTRAINT FK_OBRIGACAO_VERSAO FOREIGN KEY (ID_VERSAO_PROTOCOLO)
	  REFERENCES TB_CLV_VERSAO_PROTOCOLO (ID_VERSAO_PROTOCOLO) ENABLE;

ALTER TABLE TB_CLV_OBRIGACAO_TRANSICAO ADD CONSTRAINT FK_TRANSICAO_OBRIGACAO FOREIGN KEY (ID_OBRIGACAO)
	  REFERENCES TB_CLV_OBRIGACAO (ID_OBRIGACAO) ENABLE;


-- ------------------------------------------------------------
-- 4. Indices secundarios
--   Os de PK e UNIQUE nascem com as constraints do bloco 1; recria-los daria ORA-01408.
-- ------------------------------------------------------------

CREATE INDEX IX_OBRIGACAO_CORR ON TB_CLV_OBRIGACAO (DS_CORRELATION_ID);
CREATE INDEX IX_OBRIGACAO_CTRL ON TB_CLV_OBRIGACAO (ID_CLINICA, FL_GRUPO_CONTROLE, DS_STATUS);
CREATE INDEX IX_OBRIGACAO_PET ON TB_CLV_OBRIGACAO (ID_PET, DS_STATUS);
CREATE INDEX IX_OBRIGACAO_TENANT ON TB_CLV_OBRIGACAO (ID_CLINICA, DS_STATUS, DT_PREVISTA);
CREATE INDEX IX_TRANSICAO_OBRIGACAO ON TB_CLV_OBRIGACAO_TRANSICAO (ID_OBRIGACAO, DT_OCORRENCIA);
CREATE INDEX IX_AUDIT_OBRIGACAO ON TB_CLV_AUDIT_OBRIGACAO (ID_OBRIGACAO, NR_SEQ);
CREATE INDEX IX_OUTBOX_CORR ON TB_CLV_OUTBOX_EVENT (DS_CORRELATION_ID);
CREATE INDEX IX_OUTBOX_PENDENTE ON TB_CLV_OUTBOX_EVENT (DS_STATUS, DT_OCORRENCIA);

-- ------------------------------------------------------------
-- 5. Log de erro (chamado por tudo que vem depois)
-- ------------------------------------------------------------

CREATE OR REPLACE PROCEDURE PR_CLV_LOG_ERRO (
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
END PR_CLV_LOG_ERRO;
/


-- ------------------------------------------------------------
-- 6. Funcoes de apoio do motor
-- ------------------------------------------------------------

CREATE OR REPLACE FUNCTION FN_CLV_IDADE_MESES (
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
END FN_CLV_IDADE_MESES;
/

CREATE OR REPLACE FUNCTION FN_CLV_PESO_ATUAL (
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
END FN_CLV_PESO_ATUAL;
/

CREATE OR REPLACE FUNCTION FN_CLV_FASE_VIDA (
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

    v_idade := FN_CLV_IDADE_MESES(p_id_pet, p_data_ref);
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
END FN_CLV_FASE_VIDA;
/

CREATE OR REPLACE FUNCTION FN_CLV_CALCULAR_DATA (
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
END FN_CLV_CALCULAR_DATA;
/

CREATE OR REPLACE FUNCTION FN_CLV_GRUPO_CONTROLE (
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
END FN_CLV_GRUPO_CONTROLE;
/

CREATE OR REPLACE FUNCTION FN_CLV_AVALIA_CONDICAO (
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
            v_valor_n  := FN_CLV_IDADE_MESES(p_id_pet, p_data_ref);
            v_numerico := TRUE;

        WHEN 'PESO_KG' THEN
            v_valor_n  := FN_CLV_PESO_ATUAL(p_id_pet, p_data_ref);
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
        PR_CLV_LOG_ERRO('FN_CLV_AVALIA_CONDICAO', SQLCODE,
                        'condicao=' || p_id_condicao || ' pet=' || p_id_pet || ' ' || SQLERRM);
        RETURN 'N';
END FN_CLV_AVALIA_CONDICAO;
/

CREATE OR REPLACE FUNCTION FN_CLV_AVALIA_REGRA (
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
        v_res := FN_CLV_AVALIA_CONDICAO(c.id_condicao_prot, p_id_pet, p_data_ref, p_contexto);
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
        PR_CLV_LOG_ERRO('FN_CLV_AVALIA_REGRA', SQLCODE,
                        'regra=' || p_id_regra || ' pet=' || p_id_pet || ' ' || SQLERRM);
        RETURN 'N';
END FN_CLV_AVALIA_REGRA;
/

CREATE OR REPLACE FUNCTION FN_CLV_VERIFICA_CADEIA (
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

        -- A cadeia Ã© por obrigaÃ§Ã£o: reinicia a cada troca.
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
END FN_CLV_VERIFICA_CADEIA;
/


-- ------------------------------------------------------------
-- 7. Procedures do motor
-- ------------------------------------------------------------

CREATE OR REPLACE PROCEDURE PR_CLV_GERAR_OBRIGACOES (
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

    v_grupo := FN_CLV_GRUPO_CONTROLE(p_id_clinica, p_id_pet, 1, 10, v_seed, v_hash);

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

        -- (a) Guarda de cobertura: jÃ¡ existe obrigaÃ§Ã£o viva e futura
        -- desta versÃ£o para este pet? EntÃ£o o protocolo jÃ¡ estÃ¡ em
        -- andamento e nÃ£o deve recomeÃ§ar.
        SELECT COUNT(*) INTO v_ja_coberto
          FROM TB_CLV_OBRIGACAO
         WHERE id_pet              = p_id_pet
           AND id_versao_protocolo = reg.id_versao_protocolo
           AND dt_prevista        >= TRUNC(p_data_referencia)
           AND ds_status NOT IN ('CANCELADA','PERDIDA');

        IF v_ja_coberto > 0 THEN
            CONTINUE;
        END IF;

        IF FN_CLV_AVALIA_REGRA(reg.id_regra_ativacao, p_id_pet,
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

                v_data := FN_CLV_CALCULAR_DATA(et.id_etapa, p_data_referencia, v_data_ant);
                v_datas(et.id_etapa) := v_data;

                v_valor      := NVL(et.nr_valor_referencia, v_ticket);
                v_ocorrencia := 1;

                LOOP
                    EXIT WHEN v_data IS NULL OR v_data > v_limite;

                    -- (b) Limite de repetiÃ§Ãµes da etapa recorrente
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
        PR_CLV_LOG_ERRO('PR_CLV_GERAR_OBRIGACOES', SQLCODE,
                        'pet=' || p_id_pet || ' gatilho=' || p_evento_gatilho || ' ' || SQLERRM);
        RAISE;
END PR_CLV_GERAR_OBRIGACOES;
/

CREATE OR REPLACE PROCEDURE PR_CLV_TRANSITAR_OBRIGACAO (
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
        PR_CLV_LOG_ERRO('PR_CLV_TRANSITAR_OBRIGACAO', SQLCODE,
                        'obrigacao=' || p_id_obrigacao || ' ' || SQLERRM);
        RAISE;
END PR_CLV_TRANSITAR_OBRIGACAO;
/


-- ------------------------------------------------------------
-- 8. Trigger da trilha de auditoria encadeada
-- ------------------------------------------------------------

CREATE OR REPLACE EDITIONABLE TRIGGER TR_CLV_AUDIT_OBRIGACAO 
AFTER INSERT OR UPDATE OR DELETE ON TB_CLV_OBRIGACAO
FOR EACH ROW
DECLARE
    v_op       VARCHAR2(10);
    v_old      VARCHAR2(4000);
    v_new      VARCHAR2(4000);
    v_id_obr   NUMBER(19);
    v_clinica  NUMBER(19);
    v_anterior VARCHAR2(64);
    v_seq      NUMBER(19);
    v_hash     VARCHAR2(64);
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
END TR_CLV_AUDIT_OBRIGACAO;

/
ALTER TRIGGER TR_CLV_AUDIT_OBRIGACAO ENABLE;

-- ------------------------------------------------------------
-- 9. View do painel de receita
-- ------------------------------------------------------------

CREATE OR REPLACE FORCE EDITIONABLE VIEW VW_CLV_PAINEL_RECEITA (ID_CLINICA, MES_REFERENCIA, FL_GRUPO_CONTROLE, QT_OBRIGACOES, QT_NOTIFICADAS, QT_RESPONDIDAS, QT_AGENDADAS, QT_CUMPRIDAS, QT_PERDIDAS, PC_COMPARECIMENTO, VL_RECUPERADO, VL_PERDIDO) AS 
  SELECT o.id_clinica,
       TRUNC(o.dt_prevista, 'MM')                                    AS mes_referencia,
       o.fl_grupo_controle,
       COUNT(*)                                                      AS qt_obrigacoes,
       SUM(CASE WHEN o.ds_status IN ('NOTIFICADA','RESPONDIDA','AGENDADA','CUMPRIDA')
                THEN 1 ELSE 0 END)                                   AS qt_notificadas,
       SUM(CASE WHEN o.ds_status IN ('RESPONDIDA','AGENDADA','CUMPRIDA')
                THEN 1 ELSE 0 END)                                   AS qt_respondidas,
       SUM(CASE WHEN o.ds_status IN ('AGENDADA','CUMPRIDA')
                THEN 1 ELSE 0 END)                                   AS qt_agendadas,
       SUM(CASE WHEN o.ds_status = 'CUMPRIDA' THEN 1 ELSE 0 END)     AS qt_cumpridas,
       SUM(CASE WHEN o.ds_status = 'PERDIDA'  THEN 1 ELSE 0 END)     AS qt_perdidas,
       ROUND(100 * SUM(CASE WHEN o.ds_status = 'CUMPRIDA' THEN 1 ELSE 0 END)
             / NULLIF(COUNT(*), 0), 1)                               AS pc_comparecimento,
       SUM(NVL(o.nr_valor_realizado, 0))                             AS vl_recuperado,
       SUM(CASE WHEN o.ds_status = 'PERDIDA'
                THEN NVL(o.nr_valor_estimado, 0) ELSE 0 END)         AS vl_perdido
  FROM TB_CLV_OBRIGACAO o
 WHERE o.dt_prevista < TRUNC(SYSDATE)
 GROUP BY o.id_clinica, TRUNC(o.dt_prevista, 'MM'), o.fl_grupo_controle;
