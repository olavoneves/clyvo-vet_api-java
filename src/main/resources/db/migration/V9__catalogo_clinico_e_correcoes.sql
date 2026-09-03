-- ============================================================
-- CLYVO VET — V9 — Catálogo clínico e correções do motor
--
-- O catálogo nunca entrou na cadeia de migrations: V7 e V8 foram
-- reconstruídas do schema da FIAP com DBMS_METADATA, que extrai DDL e
-- não DML. Resultado medido num Oracle limpo, depois das 10 migrations:
-- zero espécies, zero raças, zero protocolos. Schema correto e motor sem
-- nada para disparar.
--
-- Por que V9 e não uma edição do V7: na FIAP o baseline do Flyway está em
-- 8, então V7 editada nunca voltaria a executar lá. V9 roda nos dois
-- lados — no banco vazio carrega tudo, na FIAP aplica só o que falta.
--
-- Conteúdo, nesta ordem:
--   Partes 1 a 5 — o catálogo do V4 original: espécies, 18 raças com
--                  grupo/porte/braquicefalia/predisposições, 6 tipos de
--                  condição, e os 26 protocolos (16 caninos, 10 felinos)
--                  com regras, condições, parâmetros por porte e etapas.
--   Parte 6      — condição de espécie em toda regra que não tem, senão
--                  gata recebe protocolo canino.
--   Parte 7      — limite de ocorrências na vermifugação de filhote,
--                  senão ela gera dose a cada 15 dias indefinidamente.
--
-- Sem ALTER TABLE: nr_max_ocorrencias já nasce no CREATE da V7.
-- Sem seed: dado de demonstração sai de PR_CLV_SEED_EXECUTAR, à mão.
--
-- As Partes 3 e 4 são guardadas por espécie e não reexecutam onde o
-- catálogo já existe — o loader insere sem NOT EXISTS e ds_codigo não
-- tem UNIQUE, então sem a guarda a FIAP ganharia 26 protocolos
-- duplicados em silêncio. As Partes 6 e 7 são idempotentes por
-- construção e podem rodar sempre.
--
-- REVISÃO CLÍNICA OBRIGATÓRIA. Os intervalos seguem prática comum no
-- Brasil mas variam por fabricante e por conduta do responsável técnico.
-- Cada versão tem ds_fonte_clinica para registrar quem validou.
-- ============================================================

-- ============================================================
-- PARTE 1 — ESPÉCIES E RAÇAS
-- MERGE em vez de INSERT: não duplica o que já existe.
-- ============================================================

MERGE INTO TB_CLV_ESPECIE d
USING (SELECT 'CANINA' nm FROM dual UNION ALL SELECT 'FELINA' FROM dual) s
   ON (UPPER(d.nm_especie) = s.nm)
 WHEN NOT MATCHED THEN INSERT (nm_especie, ds_especie) VALUES (s.nm, s.nm);
COMMIT;

-- Enriquecimento de raça. Raças já existentes são atualizadas;
-- as ausentes são criadas.
DECLARE
  TYPE t_raca IS RECORD (
    esp VARCHAR2(10), nome VARCHAR2(80), grupo VARCHAR2(60),
    porte VARCHAR2(10), braq CHAR(1), pred VARCHAR2(400));
  TYPE t_lista IS TABLE OF t_raca;
  v_lista t_lista := t_lista(
    t_raca('CANINA','Golden Retriever','RETRIEVER','GRANDE','N','DISPLASIA_COXOFEMORAL;CARDIOPATIA'),
    t_raca('CANINA','Labrador Retriever','RETRIEVER','GRANDE','N','DISPLASIA_COXOFEMORAL;OBESIDADE'),
    t_raca('CANINA','Pastor Alemao','PASTOR','GRANDE','N','DISPLASIA_COXOFEMORAL'),
    t_raca('CANINA','Rottweiler','MOLOSSO','GRANDE','N','DISPLASIA_COXOFEMORAL'),
    t_raca('CANINA','Dogue Alemao','MOLOSSO','GIGANTE','N','CARDIOPATIA;TORCAO_GASTRICA'),
    t_raca('CANINA','Bulldog Frances','MOLOSSO','PEQUENO','S','BRAQUICEFALIA;DOENCA_PERIODONTAL'),
    t_raca('CANINA','Pug','MOLOSSO','PEQUENO','S','BRAQUICEFALIA;DOENCA_PERIODONTAL;OBESIDADE'),
    t_raca('CANINA','Shih Tzu','TOY','PEQUENO','S','DOENCA_PERIODONTAL;OFTALMOPATIA'),
    t_raca('CANINA','Yorkshire Terrier','TOY','MINI','N','DOENCA_PERIODONTAL;LUXACAO_PATELAR'),
    t_raca('CANINA','Cavalier King Charles','TOY','PEQUENO','N','CARDIOPATIA_MITRAL'),
    t_raca('CANINA','Poodle','TOY','PEQUENO','N','DOENCA_PERIODONTAL;LUXACAO_PATELAR'),
    t_raca('CANINA','Border Collie','PASTOR','MEDIO','N',NULL),
    t_raca('CANINA','Sem raca definida','SRD','MEDIO','N',NULL),
    t_raca('FELINA','Persa','PERSA','PEQUENO','S','DRC_POLICISTICA;BRAQUICEFALIA'),
    t_raca('FELINA','Himalaio','PERSA','PEQUENO','S','DRC_POLICISTICA'),
    t_raca('FELINA','Maine Coon','MAINE_COON','MEDIO','N','CARDIOMIOPATIA_HIPERTROFICA'),
    t_raca('FELINA','Siames','SIAMES','PEQUENO','N','AMILOIDOSE'),
    t_raca('FELINA','Sem raca definida','SRD','PEQUENO','N',NULL));
  v_id_esp NUMBER(19);
  v_qtd    NUMBER;
BEGIN
  FOR i IN 1 .. v_lista.COUNT LOOP
    SELECT MIN(id_especie) INTO v_id_esp
      FROM TB_CLV_ESPECIE
     WHERE UPPER(nm_especie) LIKE SUBSTR(v_lista(i).esp,1,3) || '%';

    -- A chave e (nome, especie), nao o nome sozinho: 'Sem raca definida'
    -- existe nas duas especies. So pelo nome, a entrada felina virava UPDATE
    -- da canina e a tabela terminava com 17 racas em vez de 18 — com o gato
    -- SRD apontando para uma raca canina.
    SELECT COUNT(*) INTO v_qtd
      FROM TB_CLV_RACA
     WHERE UPPER(nm_raca) = UPPER(v_lista(i).nome)
       AND id_especie = v_id_esp;

    IF v_qtd = 0 THEN
      INSERT INTO TB_CLV_RACA (nm_raca, id_especie, ds_grupo_raca,
                               ds_porte_padrao, fl_braquicefalico, ds_predisposicoes)
      VALUES (v_lista(i).nome, v_id_esp, v_lista(i).grupo,
              v_lista(i).porte, v_lista(i).braq, v_lista(i).pred);
    ELSE
      UPDATE TB_CLV_RACA
         SET ds_grupo_raca     = v_lista(i).grupo,
             ds_porte_padrao   = v_lista(i).porte,
             fl_braquicefalico = v_lista(i).braq,
             ds_predisposicoes = v_lista(i).pred
       WHERE UPPER(nm_raca) = UPPER(v_lista(i).nome)
         AND id_especie = v_id_esp;
    END IF;
  END LOOP;
  COMMIT;
END;
/

-- Tipos de condição referenciados pelo catálogo.
DECLARE
  PROCEDURE add_cond(p_nome VARCHAR2, p_cat VARCHAR2) IS
    v NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v FROM TB_CLV_TIPO_CONDICAO WHERE nm_condicao = p_nome;
    IF v = 0 THEN
      INSERT INTO TB_CLV_TIPO_CONDICAO (nm_condicao, ds_categoria) VALUES (p_nome, p_cat);
    END IF;
  END;
BEGIN
  add_cond('Doenca renal cronica','RENAL');
  add_cond('Diabetes mellitus','ENDOCRINO');
  add_cond('Cardiopatia mitral','CARDIACO');
  add_cond('Displasia coxofemoral','ORTOPEDICO');
  add_cond('Doenca periodontal','ODONTOLOGICO');
  add_cond('Obesidade','METABOLICO');
  COMMIT;
END;
/


-- ============================================================
-- PARTE 2 — LOADER TEMPORÁRIO
-- Deixa o catálogo legível. Removido ao final do script.
-- ============================================================

CREATE OR REPLACE PACKAGE PKG_CLV_SEED_CAT AS
  g_versao   NUMBER(19);
  g_condicao NUMBER(19);
  FUNCTION prot(p_cod VARCHAR2, p_nome VARCHAR2, p_esp VARCHAR2,
                p_cat VARCHAR2, p_desc VARCHAR2) RETURN NUMBER;
  FUNCTION regra(p_versao NUMBER, p_gatilho VARCHAR2) RETURN NUMBER;
  PROCEDURE cond(p_regra NUMBER, p_attr VARCHAR2, p_oper VARCHAR2,
                 p_txt VARCHAR2 DEFAULT NULL, p_min NUMBER DEFAULT NULL,
                 p_max NUMBER DEFAULT NULL, p_porte CHAR DEFAULT 'N');
  PROCEDURE porte(p_porte VARCHAR2, p_min NUMBER, p_max NUMBER DEFAULT NULL);
  FUNCTION etapa(p_versao NUMBER, p_ordem NUMBER, p_nome VARCHAR2, p_tipo VARCHAR2,
                 p_ref NUMBER DEFAULT NULL, p_offset NUMBER DEFAULT 0,
                 p_antes NUMBER DEFAULT 7, p_depois NUMBER DEFAULT 30,
                 p_recor NUMBER DEFAULT NULL, p_proc VARCHAR2 DEFAULT NULL,
                 p_valor NUMBER DEFAULT NULL) RETURN NUMBER;
END PKG_CLV_SEED_CAT;
/

CREATE OR REPLACE PACKAGE BODY PKG_CLV_SEED_CAT AS

  FUNCTION prot(p_cod VARCHAR2, p_nome VARCHAR2, p_esp VARCHAR2,
                p_cat VARCHAR2, p_desc VARCHAR2) RETURN NUMBER IS
    v_prot NUMBER(19);
    v_ver  NUMBER(19);
  BEGIN
    INSERT INTO TB_CLV_PROTOCOLO (ds_codigo, nm_protocolo, ds_especie, ds_categoria, ds_descricao)
      VALUES (p_cod, p_nome, p_esp, p_cat, p_desc)
      RETURNING id_protocolo INTO v_prot;

    INSERT INTO TB_CLV_VERSAO_PROTOCOLO (id_protocolo, nr_versao, ds_status, ds_fonte_clinica)
      VALUES (v_prot, 1, 'VIGENTE', 'PENDENTE DE VALIDACAO PELO RESPONSAVEL TECNICO')
      RETURNING id_versao_protocolo INTO v_ver;

    g_versao := v_ver;
    RETURN v_ver;
  END;

  FUNCTION regra(p_versao NUMBER, p_gatilho VARCHAR2) RETURN NUMBER IS
    v NUMBER(19);
  BEGIN
    INSERT INTO TB_CLV_REGRA_ATIVACAO (id_versao_protocolo, ds_evento_gatilho)
      VALUES (p_versao, p_gatilho)
      RETURNING id_regra_ativacao INTO v;
    RETURN v;
  END;

  PROCEDURE cond(p_regra NUMBER, p_attr VARCHAR2, p_oper VARCHAR2,
                 p_txt VARCHAR2 DEFAULT NULL, p_min NUMBER DEFAULT NULL,
                 p_max NUMBER DEFAULT NULL, p_porte CHAR DEFAULT 'N') IS
    v NUMBER(19);
  BEGIN
    INSERT INTO TB_CLV_CONDICAO_PROT (id_regra_ativacao, ds_atributo, ds_operador,
                                      ds_valor_texto, nr_valor_min, nr_valor_max, fl_por_porte)
      VALUES (p_regra, p_attr, p_oper, p_txt, p_min, p_max, p_porte)
      RETURNING id_condicao_prot INTO v;
    g_condicao := v;
  END;

  PROCEDURE porte(p_porte VARCHAR2, p_min NUMBER, p_max NUMBER DEFAULT NULL) IS
  BEGIN
    INSERT INTO TB_CLV_PARAM_PORTE (id_condicao_prot, ds_porte, nr_valor_min, nr_valor_max)
      VALUES (g_condicao, p_porte, p_min, p_max);
  END;

  FUNCTION etapa(p_versao NUMBER, p_ordem NUMBER, p_nome VARCHAR2, p_tipo VARCHAR2,
                 p_ref NUMBER DEFAULT NULL, p_offset NUMBER DEFAULT 0,
                 p_antes NUMBER DEFAULT 7, p_depois NUMBER DEFAULT 30,
                 p_recor NUMBER DEFAULT NULL, p_proc VARCHAR2 DEFAULT NULL,
                 p_valor NUMBER DEFAULT NULL) RETURN NUMBER IS
    v NUMBER(19);
  BEGIN
    INSERT INTO TB_CLV_ETAPA_PROTOCOLO (id_versao_protocolo, nr_ordem, nm_etapa,
           ds_tipo_intervalo, id_etapa_referencia, nr_offset_dias,
           nr_janela_antes, nr_janela_depois, nr_recorrencia_dias,
           ds_procedimento, nr_valor_referencia)
      VALUES (p_versao, p_ordem, p_nome, p_tipo, p_ref, p_offset,
              p_antes, p_depois, p_recor, p_proc, p_valor)
      RETURNING id_etapa INTO v;
    RETURN v;
  END;

END PKG_CLV_SEED_CAT;
/


-- ============================================================
-- PARTE 3 — CATÁLOGO CANINO
-- ============================================================
DECLARE
  p PKG_CLV_SEED_CAT.g_versao%TYPE;
  v NUMBER(19); r NUMBER(19); e1 NUMBER(19); e2 NUMBER(19); e3 NUMBER(19);
  v_ja NUMBER;
BEGIN

  -- Onde o catalogo canino ja existe (FIAP), nao reinsere.
  SELECT COUNT(*) INTO v_ja FROM TB_CLV_PROTOCOLO WHERE ds_especie = 'CANINA';
  IF v_ja > 0 THEN
    RETURN;
  END IF;

  -- 01 · Série V10 filhote. Caso estrutural: APOS_ETAPA encadeado
  -- transicionando para regime recorrente anual.
  v := PKG_CLV_SEED_CAT.prot('CAN_V10_SERIE','Serie V10 (filhote)','CANINA','VACINA',
       'Tres doses com 21 dias de intervalo a partir de 45 dias de vida, seguidas de reforco anual.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','BETWEEN',NULL,1.5,4);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'V10 1a dose','APOS_REFERENCIA',NULL,0,3,15,NULL,'VAC_V10',120);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'V10 2a dose','APOS_ETAPA',e1,21,3,15,NULL,'VAC_V10',120);
  e3 := PKG_CLV_SEED_CAT.etapa(v,3,'V10 3a dose','APOS_ETAPA',e2,21,3,15,NULL,'VAC_V10',120);
  e1 := PKG_CLV_SEED_CAT.etapa(v,4,'V10 reforco anual','RECORRENTE',e3,365,15,45,365,'VAC_V10',130);

  -- 02 · Antirrábica anual
  v := PKG_CLV_SEED_CAT.prot('CAN_ANTIRRABICA','Antirrabica anual','CANINA','VACINA',
       'Primeira dose a partir de 120 dias, reforco anual.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,4);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Antirrabica dose','APOS_REFERENCIA',NULL,0,7,30,NULL,'VAC_RAIVA',90);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Antirrabica reforco anual','RECORRENTE',e1,365,15,45,365,'VAC_RAIVA',90);

  -- 03 · Tosse dos canis
  v := PKG_CLV_SEED_CAT.prot('CAN_TOSSE_CANIL','Tosse dos canis','CANINA','VACINA',
       'Duas doses com 21 dias, reforco anual.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,2);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Tosse dos canis 1a dose','APOS_REFERENCIA',NULL,0,7,30,NULL,'VAC_TOSSE',110);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Tosse dos canis 2a dose','APOS_ETAPA',e1,21,3,15,NULL,'VAC_TOSSE',110);
  e3 := PKG_CLV_SEED_CAT.etapa(v,3,'Tosse dos canis reforco','RECORRENTE',e2,365,15,45,365,'VAC_TOSSE',110);

  -- 04 · Giárdia
  v := PKG_CLV_SEED_CAT.prot('CAN_GIARDIA','Vacina Giardia','CANINA','VACINA',
       'Duas doses com 21 dias, reforco anual. Indicada em ambiente coletivo.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,2);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Giardia 1a dose','APOS_REFERENCIA',NULL,0,7,30,NULL,'VAC_GIARDIA',100);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Giardia 2a dose','APOS_ETAPA',e1,21,3,15,NULL,'VAC_GIARDIA',100);
  e3 := PKG_CLV_SEED_CAT.etapa(v,3,'Giardia reforco','RECORRENTE',e2,365,15,45,365,'VAC_GIARDIA',100);

  -- 05 · Vermifugação filhote: cadência quinzenal curta
  v := PKG_CLV_SEED_CAT.prot('CAN_VERMIF_FILHOTE','Vermifugacao de filhote','CANINA','VERMIFUGO',
       'A cada 15 dias entre 30 e 90 dias de vida.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','BETWEEN',NULL,1,3);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Vermifugo dose','APOS_REFERENCIA',NULL,0,2,7,NULL,'VERM_ORAL',60);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Vermifugo repeticao quinzenal','RECORRENTE',e1,15,2,7,15,'VERM_ORAL',60);

  -- 06 · Vermifugação adulto por faixa de peso.
  -- Caso estrutural: condição sobre PESO_KG (vem da anamnese mais recente).
  v := PKG_CLV_SEED_CAT.prot('CAN_VERMIF_ADULTO_P','Vermifugacao adulto ate 10 kg','CANINA','VERMIFUGO',
       'Trimestral. Dose por faixa de peso.');
  r := PKG_CLV_SEED_CAT.regra(v,'PESO_ATUALIZADO');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,3);
  PKG_CLV_SEED_CAT.cond(r,'PESO_KG','BETWEEN',NULL,0,10);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Vermifugo dose ate 10 kg','APOS_REFERENCIA',NULL,0,7,30,NULL,'VERM_P',55);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Vermifugo trimestral','RECORRENTE',e1,90,10,30,90,'VERM_P',55);

  v := PKG_CLV_SEED_CAT.prot('CAN_VERMIF_ADULTO_M','Vermifugacao adulto 10 a 25 kg','CANINA','VERMIFUGO',
       'Trimestral. Dose por faixa de peso.');
  r := PKG_CLV_SEED_CAT.regra(v,'PESO_ATUALIZADO');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,3);
  PKG_CLV_SEED_CAT.cond(r,'PESO_KG','BETWEEN',NULL,10.01,25);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Vermifugo dose 10 a 25 kg','APOS_REFERENCIA',NULL,0,7,30,NULL,'VERM_M',70);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Vermifugo trimestral','RECORRENTE',e1,90,10,30,90,'VERM_M',70);

  v := PKG_CLV_SEED_CAT.prot('CAN_VERMIF_ADULTO_G','Vermifugacao adulto acima de 25 kg','CANINA','VERMIFUGO',
       'Trimestral. Dose por faixa de peso.');
  r := PKG_CLV_SEED_CAT.regra(v,'PESO_ATUALIZADO');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,3);
  PKG_CLV_SEED_CAT.cond(r,'PESO_KG','GTE',NULL,25.01);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Vermifugo dose acima de 25 kg','APOS_REFERENCIA',NULL,0,7,30,NULL,'VERM_G',90);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Vermifugo trimestral','RECORRENTE',e1,90,10,30,90,'VERM_G',90);

  -- 07 · Castração: janela varia por porte. Caso estrutural: PARAM_PORTE.
  v := PKG_CLV_SEED_CAT.prot('CAN_CASTRACAO','Janela de castracao','CANINA','CIRURGIA',
       'Janela recomendada varia por porte: raca grande amadurece mais tarde.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'CASTRADO','EQ','N');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','BETWEEN',NULL,NULL,NULL,'S');
    PKG_CLV_SEED_CAT.porte('MINI',5,10);
    PKG_CLV_SEED_CAT.porte('PEQUENO',5,10);
    PKG_CLV_SEED_CAT.porte('MEDIO',7,14);
    PKG_CLV_SEED_CAT.porte('GRANDE',10,20);
    PKG_CLV_SEED_CAT.porte('GIGANTE',14,26);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Avaliacao pre-cirurgica','APOS_REFERENCIA',NULL,0,15,60,NULL,'AVAL_PRE_CIR',180);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Castracao','APOS_ETAPA',e1,15,7,45,NULL,'CIR_CASTRACAO',900);
  e3 := PKG_CLV_SEED_CAT.etapa(v,3,'Retorno pos-cirurgico','APOS_ETAPA',e2,10,2,7,NULL,'RET_POS_CIR',0);

  -- 08 · Profilaxia dentária: antecipada em porte pequeno
  v := PKG_CLV_SEED_CAT.prot('CAN_ODONTO','Profilaxia dentaria','CANINA','ODONTO',
       'Anual. Inicio antecipado em raca pequena e braquicefalica.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,NULL,NULL,'S');
    PKG_CLV_SEED_CAT.porte('MINI',24,NULL);
    PKG_CLV_SEED_CAT.porte('PEQUENO',24,NULL);
    PKG_CLV_SEED_CAT.porte('MEDIO',36,NULL);
    PKG_CLV_SEED_CAT.porte('GRANDE',36,NULL);
    PKG_CLV_SEED_CAT.porte('GIGANTE',36,NULL);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Avaliacao odontologica','APOS_REFERENCIA',NULL,0,15,60,NULL,'AVAL_ODONTO',150);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Profilaxia anual','RECORRENTE',e1,365,20,60,365,'ODONTO_PROF',650);

  -- 09 · Check-up sênior: limiar por porte. Gigante envelhece antes.
  v := PKG_CLV_SEED_CAT.prot('CAN_CHECKUP_SENIOR','Check-up senior','CANINA','CHECKUP',
       'Semestral a partir da entrada na fase senior, que varia por porte.');
  r := PKG_CLV_SEED_CAT.regra(v,'FASE_VIDA_ALTERADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','CANINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,NULL,NULL,'S');
    PKG_CLV_SEED_CAT.porte('MINI',96,NULL);
    PKG_CLV_SEED_CAT.porte('PEQUENO',96,NULL);
    PKG_CLV_SEED_CAT.porte('MEDIO',84,NULL);
    PKG_CLV_SEED_CAT.porte('GRANDE',72,NULL);
    PKG_CLV_SEED_CAT.porte('GIGANTE',60,NULL);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Check-up senior hemograma e bioquimico','APOS_REFERENCIA',NULL,0,15,60,NULL,'LAB_SENIOR',320);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Check-up senior semestral','RECORRENTE',e1,182,15,45,182,'LAB_SENIOR',320);

  -- 10 · Displasia coxofemoral: predisposição por grupo de raça
  v := PKG_CLV_SEED_CAT.prot('CAN_DISPLASIA_RX','Triagem de displasia coxofemoral','CANINA','EXAME',
       'Radiografia de quadril entre 12 e 18 meses em racas predispostas.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'GRUPO_RACA','IN','RETRIEVER,PASTOR,MOLOSSO');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','BETWEEN',NULL,12,18);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Radiografia de quadril','APOS_REFERENCIA',NULL,0,15,60,NULL,'RX_QUADRIL',380);

  -- 11 · Cardiopatia mitral: predisposição por raça específica
  v := PKG_CLV_SEED_CAT.prot('CAN_CARDIO_MITRAL','Monitoramento cardiaco','CANINA','MONITORAMENTO',
       'Ecocardiograma anual a partir dos 4 anos em racas predispostas a doenca mitral.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'RACA','IN','Cavalier King Charles,Dogue Alemao');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,48);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Ecocardiograma','APOS_REFERENCIA',NULL,0,15,60,NULL,'ECO_CARDIO',450);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Ecocardiograma anual','RECORRENTE',e1,365,20,60,365,'ECO_CARDIO',450);

  -- 12 · DRC: ativado por diagnóstico. Caso estrutural: CONDICAO_DIAGNOSTICADA.
  v := PKG_CLV_SEED_CAT.prot('CAN_DRC_MONITOR','Monitoramento de doenca renal cronica','CANINA','MONITORAMENTO',
       'Reavaliacao a cada 90 dias apos diagnostico de DRC.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONDICAO_DIAGNOSTICADA');
  PKG_CLV_SEED_CAT.cond(r,'CONDICAO','IN','Doenca renal cronica');
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Reavaliacao renal','APOS_REFERENCIA',NULL,30,5,15,NULL,'LAB_RENAL',290);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Reavaliacao renal trimestral','RECORRENTE',e1,90,7,21,90,'LAB_RENAL',290);

  -- 13 · Diabetes: curva glicêmica em 30 dias, depois trimestral
  v := PKG_CLV_SEED_CAT.prot('CAN_DM_MONITOR','Monitoramento de diabetes mellitus','CANINA','MONITORAMENTO',
       'Curva glicemica em 30 dias apos diagnostico, depois trimestral.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONDICAO_DIAGNOSTICADA');
  PKG_CLV_SEED_CAT.cond(r,'CONDICAO','IN','Diabetes mellitus');
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Curva glicemica','APOS_REFERENCIA',NULL,30,3,10,NULL,'CURVA_GLIC',260);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Curva glicemica trimestral','RECORRENTE',e1,90,7,21,90,'CURVA_GLIC',260);

  -- 14 · Pós-operatório: dois retornos curtos
  v := PKG_CLV_SEED_CAT.prot('CAN_POS_OP','Retorno pos-operatorio','CANINA','RETORNO',
       'Retorno em 3 dias e retirada de pontos em 10 dias.');
  r := PKG_CLV_SEED_CAT.regra(v,'PROCEDIMENTO_REALIZADO');
  PKG_CLV_SEED_CAT.cond(r,'PROCEDIMENTO','IN','CIR_CASTRACAO,CIR_GERAL,ODONTO_PROF');
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Retorno avaliacao de ferida','APOS_REFERENCIA',NULL,3,1,3,NULL,'RET_POS_OP',0);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Retirada de pontos','APOS_ETAPA',e1,7,1,4,NULL,'RET_PONTOS',80);

  COMMIT;
END;
/


-- ============================================================
-- PARTE 4 — CATÁLOGO FELINO
-- ============================================================
DECLARE
  v NUMBER(19); r NUMBER(19); e1 NUMBER(19); e2 NUMBER(19); e3 NUMBER(19);
  v_ja NUMBER;
BEGIN

  -- Idem para o felino: a guarda e por especie, senao a Parte 4 seria
  -- pulada so porque a Parte 3 acabou de inserir os caninos.
  SELECT COUNT(*) INTO v_ja FROM TB_CLV_PROTOCOLO WHERE ds_especie = 'FELINA';
  IF v_ja > 0 THEN
    RETURN;
  END IF;

  -- 15 · Série V4 filhote
  v := PKG_CLV_SEED_CAT.prot('FEL_V4_SERIE','Serie V4 (filhote)','FELINA','VACINA',
       'Tres doses com 21 dias a partir de 60 dias, reforco anual.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','FELINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','BETWEEN',NULL,2,5);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'V4 1a dose','APOS_REFERENCIA',NULL,0,3,15,NULL,'VAC_V4',130);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'V4 2a dose','APOS_ETAPA',e1,21,3,15,NULL,'VAC_V4',130);
  e3 := PKG_CLV_SEED_CAT.etapa(v,3,'V4 3a dose','APOS_ETAPA',e2,21,3,15,NULL,'VAC_V4',130);
  e1 := PKG_CLV_SEED_CAT.etapa(v,4,'V4 reforco anual','RECORRENTE',e3,365,15,45,365,'VAC_V4',140);

  -- 16 · Antirrábica felina
  v := PKG_CLV_SEED_CAT.prot('FEL_ANTIRRABICA','Antirrabica felina anual','FELINA','VACINA',
       'Primeira dose a partir de 120 dias, reforco anual.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','FELINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,4);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Antirrabica dose','APOS_REFERENCIA',NULL,0,7,30,NULL,'VAC_RAIVA',95);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Antirrabica reforco anual','RECORRENTE',e1,365,15,45,365,'VAC_RAIVA',95);

  -- 17 · FeLV: teste obrigatório antes da primeira dose
  v := PKG_CLV_SEED_CAT.prot('FEL_FELV','Leucemia felina (FeLV)','FELINA','VACINA',
       'Teste FeLV antes da primeira dose, duas doses com 21 dias e reforco anual.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','FELINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,2);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Teste FeLV e FIV','APOS_REFERENCIA',NULL,0,5,20,NULL,'TESTE_FELV',180);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'FeLV 1a dose','APOS_ETAPA',e1,7,3,15,NULL,'VAC_FELV',150);
  e3 := PKG_CLV_SEED_CAT.etapa(v,3,'FeLV 2a dose','APOS_ETAPA',e2,21,3,15,NULL,'VAC_FELV',150);
  e1 := PKG_CLV_SEED_CAT.etapa(v,4,'FeLV reforco anual','RECORRENTE',e3,365,15,45,365,'VAC_FELV',150);

  -- 18 · Vermifugação felina trimestral
  v := PKG_CLV_SEED_CAT.prot('FEL_VERMIFUGO','Vermifugacao felina','FELINA','VERMIFUGO',
       'Trimestral em adultos. Dose por peso.');
  r := PKG_CLV_SEED_CAT.regra(v,'PESO_ATUALIZADO');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','FELINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,3);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Vermifugo felino dose','APOS_REFERENCIA',NULL,0,7,30,NULL,'VERM_FEL',60);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Vermifugo felino trimestral','RECORRENTE',e1,90,10,30,90,'VERM_FEL',60);

  -- 19 · Castração felina
  v := PKG_CLV_SEED_CAT.prot('FEL_CASTRACAO','Janela de castracao felina','FELINA','CIRURGIA',
       'Janela entre 6 e 9 meses.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','FELINA');
  PKG_CLV_SEED_CAT.cond(r,'CASTRADO','EQ','N');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','BETWEEN',NULL,6,9);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Avaliacao pre-cirurgica','APOS_REFERENCIA',NULL,0,10,45,NULL,'AVAL_PRE_CIR',170);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Castracao','APOS_ETAPA',e1,10,7,30,NULL,'CIR_CASTRACAO',700);
  e3 := PKG_CLV_SEED_CAT.etapa(v,3,'Retorno pos-cirurgico','APOS_ETAPA',e2,10,2,7,NULL,'RET_POS_CIR',0);

  -- 20 · Check-up sênior felino
  v := PKG_CLV_SEED_CAT.prot('FEL_CHECKUP_SENIOR','Check-up senior felino','FELINA','CHECKUP',
       'Semestral a partir dos 7 anos, com funcao renal e pressao arterial.');
  r := PKG_CLV_SEED_CAT.regra(v,'FASE_VIDA_ALTERADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','FELINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,84);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Check-up senior renal e pressao','APOS_REFERENCIA',NULL,0,15,60,NULL,'LAB_SENIOR_FEL',340);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Check-up senior semestral','RECORRENTE',e1,182,15,45,182,'LAB_SENIOR_FEL',340);

  -- 21 · Triagem renal em Persa e Himalaio: antecipa aos 5 anos
  v := PKG_CLV_SEED_CAT.prot('FEL_RENAL_PERSA','Triagem renal em raca predisposta','FELINA','EXAME',
       'Ultrassom e funcao renal anual a partir dos 5 anos em Persa e Himalaio (DRC policistica).');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'GRUPO_RACA','EQ','PERSA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,60);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Ultrassom abdominal e funcao renal','APOS_REFERENCIA',NULL,0,15,60,NULL,'US_RENAL',420);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Triagem renal anual','RECORRENTE',e1,365,20,60,365,'US_RENAL',420);

  -- 22 · Cardiomiopatia hipertrófica em Maine Coon
  v := PKG_CLV_SEED_CAT.prot('FEL_CMH_MAINECOON','Triagem de cardiomiopatia hipertrofica','FELINA','MONITORAMENTO',
       'Ecocardiograma anual a partir dos 2 anos em Maine Coon.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'RACA','EQ','Maine Coon');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,24);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Ecocardiograma','APOS_REFERENCIA',NULL,0,15,60,NULL,'ECO_CARDIO',450);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Ecocardiograma anual','RECORRENTE',e1,365,20,60,365,'ECO_CARDIO',450);

  -- 23 · DRC felina: cadência mais curta que a canina
  v := PKG_CLV_SEED_CAT.prot('FEL_DRC_MONITOR','Monitoramento de DRC felina','FELINA','MONITORAMENTO',
       'Reavaliacao em 30 dias e depois a cada 90 dias apos diagnostico.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONDICAO_DIAGNOSTICADA');
  PKG_CLV_SEED_CAT.cond(r,'CONDICAO','IN','Doenca renal cronica');
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Reavaliacao renal','APOS_REFERENCIA',NULL,30,5,15,NULL,'LAB_RENAL_FEL',300);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Reavaliacao renal trimestral','RECORRENTE',e1,90,7,21,90,'LAB_RENAL_FEL',300);

  -- 24 · Profilaxia dentária felina
  v := PKG_CLV_SEED_CAT.prot('FEL_ODONTO','Profilaxia dentaria felina','FELINA','ODONTO',
       'Avaliacao anual a partir dos 3 anos.');
  r := PKG_CLV_SEED_CAT.regra(v,'CONSULTA_REGISTRADA');
  PKG_CLV_SEED_CAT.cond(r,'ESPECIE','EQ','FELINA');
  PKG_CLV_SEED_CAT.cond(r,'IDADE_MESES','GTE',NULL,36);
  e1 := PKG_CLV_SEED_CAT.etapa(v,1,'Avaliacao odontologica','APOS_REFERENCIA',NULL,0,15,60,NULL,'AVAL_ODONTO',150);
  e2 := PKG_CLV_SEED_CAT.etapa(v,2,'Profilaxia anual','RECORRENTE',e1,365,20,60,365,'ODONTO_PROF',600);

  COMMIT;
END;
/


-- ============================================================
-- PARTE 5 — LIMPEZA
-- ============================================================
DROP PACKAGE PKG_CLV_SEED_CAT;


-- ============================================================
-- PARTE 6 — CONDIÇÃO DE ESPÉCIE EM TODA REGRA QUE NÃO TEM
-- ============================================================
-- Protocolos disparados por diagnóstico ou procedimento não declaravam
-- espécie, e o motor entregava CAN_DRC_MONITOR para gata. A espécie sai
-- do próprio protocolo, então não há como digitar errado. NOT EXISTS:
-- não duplica onde a condição já existe.

INSERT INTO TB_CLV_CONDICAO_PROT (id_regra_ativacao, ds_atributo,
                                  ds_operador, ds_valor_texto)
SELECT r.id_regra_ativacao, 'ESPECIE', 'EQ', p.ds_especie
  FROM TB_CLV_REGRA_ATIVACAO r
  JOIN TB_CLV_VERSAO_PROTOCOLO v ON v.id_versao_protocolo = r.id_versao_protocolo
  JOIN TB_CLV_PROTOCOLO p        ON p.id_protocolo        = v.id_protocolo
 WHERE NOT EXISTS (
       SELECT 1 FROM TB_CLV_CONDICAO_PROT c
        WHERE c.id_regra_ativacao = r.id_regra_ativacao
          AND c.ds_atributo = 'ESPECIE');

COMMIT;


-- ============================================================
-- PARTE 7 — LIMITE DE OCORRÊNCIAS NA VERMIFUGAÇÃO DE FILHOTE
-- ============================================================
-- Etapa RECORRENTE sem teto gerava dose a cada 15 dias indefinidamente,
-- muito além da faixa etária que a justifica. A faixa é de 30 a 90 dias
-- de vida: quatro repetições cobrem a janela inteira.

UPDATE TB_CLV_ETAPA_PROTOCOLO e
   SET e.nr_max_ocorrencias = 4
 WHERE e.ds_tipo_intervalo = 'RECORRENTE'
   AND EXISTS (SELECT 1
                 FROM TB_CLV_VERSAO_PROTOCOLO v
                 JOIN TB_CLV_PROTOCOLO p ON p.id_protocolo = v.id_protocolo
                WHERE v.id_versao_protocolo = e.id_versao_protocolo
                  AND p.ds_codigo = 'CAN_VERMIF_FILHOTE');

COMMIT;
