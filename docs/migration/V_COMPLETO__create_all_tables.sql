-- ============================================================
-- CLYVO VET — DDL COMPLETO (Oracle)
-- Criação de todas as tabelas com constraints e FKs inline
-- Ordem respeita dependências entre tabelas
--
-- Executar como: rm564495
-- Ambiente local: Oracle XE / Oracle Developer
--
-- NOTA: Este script é para uso em ambiente LOCAL (fresh install).
--       Para o banco FIAP (oracle.fiap.com.br), usar os scripts
--       individuais: V0__foreign_keys.sql e V1__veterinario_auth_e_refresh_token.sql
-- ============================================================


-- ============================================================
-- BLOCO 1 — TABELAS RAIZ (sem dependências)
-- ============================================================

CREATE TABLE TB_CLV_ESPECIE (
    id_especie   NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_especie   VARCHAR2(60)  NOT NULL,
    ds_especie   VARCHAR2(300),
    CONSTRAINT uk_especie_nome UNIQUE (nm_especie)
);

CREATE TABLE TB_CLV_CLINICA (
    id_clinica    NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_clinica    VARCHAR2(120) NOT NULL,
    ds_cnpj       VARCHAR2(14)  NOT NULL,
    ds_logradouro VARCHAR2(200) NOT NULL,
    ds_numero     VARCHAR2(10),
    ds_bairro     VARCHAR2(80),
    nm_cidade     VARCHAR2(80)  NOT NULL,
    sg_estado     VARCHAR2(2)   NOT NULL,
    nr_cep        VARCHAR2(8),
    nr_telefone   VARCHAR2(20),
    CONSTRAINT uk_clinica_cnpj UNIQUE (ds_cnpj)
);

CREATE TABLE TB_CLV_TUTOR (
    id_tutor               NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_tutor               VARCHAR2(100) NOT NULL,
    ds_email               VARCHAR2(150) NOT NULL,
    nr_telefone            VARCHAR2(20),
    nr_telefone_emergencia VARCHAR2(20),
    ds_senha_hash          VARCHAR2(255) NOT NULL,
    ds_canal_preferencial  VARCHAR2(10),
    dt_cadastro            DATE,
    CONSTRAINT uk_tutor_email    UNIQUE (ds_email),
    CONSTRAINT chk_tutor_canal   CHECK  (ds_canal_preferencial IN ('APP','WEB','WHATSAPP'))
);

CREATE TABLE TB_CLV_MEDICAMENTO (
    id_medicamento        NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_medicamento        VARCHAR2(150) NOT NULL,
    ds_principio_ativo    VARCHAR2(200),
    ds_classe_terapeutica VARCHAR2(100),
    CONSTRAINT uk_medicamento_nome UNIQUE (nm_medicamento)
);

CREATE TABLE TB_CLV_TIPO_VACINA (
    id_tipo_vacina         NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_vacina              VARCHAR2(100) NOT NULL,
    ds_fabricante          VARCHAR2(100),
    nr_dose_intervalo_dias NUMBER(10),
    CONSTRAINT uk_tipo_vacina_nome UNIQUE (nm_vacina)
);

CREATE TABLE TB_CLV_TIPO_SENSOR (
    id_tipo_sensor NUMBER(19)   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_tipo        VARCHAR2(60) NOT NULL,
    ds_unidade     VARCHAR2(20) NOT NULL,
    nr_valor_min   NUMBER,
    nr_valor_max   NUMBER,
    CONSTRAINT uk_tipo_sensor_nome UNIQUE (nm_tipo)
);

CREATE TABLE TB_CLV_TIPO_CONDICAO (
    id_tipo_condicao NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_condicao      VARCHAR2(120) NOT NULL,
    ds_categoria     VARCHAR2(60),
    CONSTRAINT uk_tipo_condicao_nome UNIQUE (nm_condicao)
);

CREATE TABLE TB_CLV_TIPO_ALERGIA (
    id_tipo_alergia NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_alergia      VARCHAR2(120) NOT NULL,
    ds_categoria    VARCHAR2(20),
    CONSTRAINT uk_tipo_alergia_nome  UNIQUE (nm_alergia),
    CONSTRAINT chk_tipo_alergia_cat  CHECK  (ds_categoria IN ('MEDICAMENTO','ALIMENTO','AMBIENTAL','MATERIAL'))
);

CREATE TABLE TB_CLV_LOG_ERRO (
    id_log           NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_procedure     VARCHAR2(100)  NOT NULL,
    nm_usuario       VARCHAR2(80)   NOT NULL,
    dt_ocorrencia    TIMESTAMP,
    nr_codigo_erro   NUMBER(19)     NOT NULL,
    ds_mensagem_erro VARCHAR2(4000) NOT NULL,
    ds_ambiente      VARCHAR2(3),
    CONSTRAINT chk_log_ambiente CHECK (ds_ambiente IN ('DEV','HOM','PRD'))
);

-- Sem FK (nr_id_usuario é polimórfico: pode referenciar TUTOR, VETERINARIO ou COLABORADOR)
-- Nota: COLABORADOR não persiste refresh token via código (sessão sem refresh)
CREATE TABLE TB_CLV_REFRESH_TOKEN (
    id_refresh_token NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ds_token         VARCHAR2(512) NOT NULL,
    ds_tipo_usuario  VARCHAR2(15)  NOT NULL,
    nr_id_usuario    NUMBER(19)    NOT NULL,
    dt_expiracao     TIMESTAMP     NOT NULL,
    fl_revogado      CHAR(1)       NOT NULL,
    dt_criacao       TIMESTAMP     NOT NULL,
    CONSTRAINT uk_refresh_token       UNIQUE (ds_token),
    CONSTRAINT chk_refresh_tipo       CHECK  (ds_tipo_usuario IN ('TUTOR','VETERINARIO','COLABORADOR')),
    CONSTRAINT chk_refresh_revogado   CHECK  (fl_revogado     IN ('S','N'))
);


-- ============================================================
-- BLOCO 2 — TABELAS COM DEPENDÊNCIAS
-- ============================================================

-- Depende: TB_CLV_ESPECIE
CREATE TABLE TB_CLV_RACA (
    id_raca    NUMBER(19)   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_raca    VARCHAR2(80) NOT NULL,
    id_especie NUMBER(19)   NOT NULL,
    CONSTRAINT fk_raca_especie FOREIGN KEY (id_especie) REFERENCES TB_CLV_ESPECIE (id_especie)
);

-- Depende: TB_CLV_CLINICA
CREATE TABLE TB_CLV_VETERINARIO (
    id_veterinario   NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_veterinario   VARCHAR2(100) NOT NULL,
    nr_crmv          VARCHAR2(20)  NOT NULL,
    ds_especialidade VARCHAR2(100),
    ds_email         VARCHAR2(150) NOT NULL,
    ds_senha_hash    VARCHAR2(255) NOT NULL,
    id_clinica       NUMBER(19)    NOT NULL,
    CONSTRAINT uk_veterinario_crmv  UNIQUE (nr_crmv),
    CONSTRAINT uk_veterinario_email UNIQUE (ds_email),
    CONSTRAINT fk_vet_clinica FOREIGN KEY (id_clinica) REFERENCES TB_CLV_CLINICA (id_clinica)
);

-- Depende: TB_CLV_TUTOR, TB_CLV_RACA
CREATE TABLE TB_CLV_PET (
    id_pet              NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_pet              VARCHAR2(100) NOT NULL,
    dt_nascimento       DATE,
    ds_sexo             VARCHAR2(1),
    nr_microchip        VARCHAR2(30),
    nr_rga              VARCHAR2(30),
    ds_pelagem          VARCHAR2(80),
    ds_porte            VARCHAR2(10),
    fl_castrado         CHAR(1),
    ds_status_pet       VARCHAR2(15),
    ds_observacao_geral VARCHAR2(2000),
    id_tutor            NUMBER(19)    NOT NULL,
    id_raca             NUMBER(19)    NOT NULL,
    CONSTRAINT uk_pet_microchip  UNIQUE (nr_microchip),
    CONSTRAINT chk_pet_sexo      CHECK  (ds_sexo      IN ('M','F','I')),
    CONSTRAINT chk_pet_porte     CHECK  (ds_porte     IN ('MINI','PEQUENO','MEDIO','GRANDE','GIGANTE')),
    CONSTRAINT chk_pet_castrado  CHECK  (fl_castrado  IN ('S','N')),
    CONSTRAINT chk_pet_status    CHECK  (ds_status_pet IN ('ATIVO','EM_TRATAMENTO','OBITO','PERDIDO')),
    CONSTRAINT fk_pet_tutor FOREIGN KEY (id_tutor) REFERENCES TB_CLV_TUTOR (id_tutor),
    CONSTRAINT fk_pet_raca  FOREIGN KEY (id_raca)  REFERENCES TB_CLV_RACA  (id_raca)
);

-- Depende: TB_CLV_PET, TB_CLV_VETERINARIO
CREATE TABLE TB_CLV_CONSULTA (
    id_consulta    NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dt_consulta    DATE           NOT NULL,
    ds_motivo      VARCHAR2(500),
    ds_diagnostico VARCHAR2(1000),
    ds_status      VARCHAR2(20),
    id_pet         NUMBER(19)     NOT NULL,
    id_veterinario NUMBER(19)     NOT NULL,
    CONSTRAINT chk_consulta_status CHECK  (ds_status IN ('AGENDADA','REALIZADA','CANCELADA','EM_ATENDIMENTO')),
    CONSTRAINT fk_consulta_pet FOREIGN KEY (id_pet)         REFERENCES TB_CLV_PET        (id_pet),
    CONSTRAINT fk_consulta_vet FOREIGN KEY (id_veterinario) REFERENCES TB_CLV_VETERINARIO (id_veterinario)
);

-- Depende: TB_CLV_PET, TB_CLV_VETERINARIO, TB_CLV_CONSULTA (nullable)
CREATE TABLE TB_CLV_AGENDAMENTO (
    id_agendamento         NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dt_agendamento         DATE          NOT NULL,
    hr_agendamento         VARCHAR2(5)   NOT NULL,
    ds_status              VARCHAR2(15),
    ds_canal_origem        VARCHAR2(10),
    ds_motivo_cancelamento VARCHAR2(300),
    id_pet                 NUMBER(19)    NOT NULL,
    id_veterinario         NUMBER(19)    NOT NULL,
    id_consulta            NUMBER(19),               -- nullable: agendamento ainda não convertido
    CONSTRAINT chk_agend_status  CHECK  (ds_status      IN ('SOLICITADO','CONFIRMADO','CANCELADO','REALIZADO')),
    CONSTRAINT chk_agend_canal   CHECK  (ds_canal_origem IN ('APP','WEB','WHATSAPP')),
    CONSTRAINT fk_agend_pet      FOREIGN KEY (id_pet)         REFERENCES TB_CLV_PET        (id_pet),
    CONSTRAINT fk_agend_vet      FOREIGN KEY (id_veterinario) REFERENCES TB_CLV_VETERINARIO (id_veterinario),
    CONSTRAINT fk_agend_consulta FOREIGN KEY (id_consulta)    REFERENCES TB_CLV_CONSULTA    (id_consulta)
);

-- Depende: TB_CLV_CONSULTA (1:1 — unique na coluna)
CREATE TABLE TB_CLV_ANAMNESE (
    id_anamnese             NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta             NUMBER(19)     NOT NULL,
    ds_queixa_principal     VARCHAR2(500)  NOT NULL,
    ds_historico_relatado   VARCHAR2(2000),
    nr_peso_kg              NUMBER(5,2)    NOT NULL,
    nr_temperatura_c        NUMBER(4,1),
    nr_freq_cardiaca        NUMBER(10),
    nr_freq_respiratoria    NUMBER(10),
    ds_condicao_corporal    VARCHAR2(12),
    ds_mucosas              VARCHAR2(100),
    ds_linfonodos           VARCHAR2(100),
    ds_observacoes_clinicas VARCHAR2(2000),
    CONSTRAINT uk_anamnese_consulta  UNIQUE (id_consulta),
    CONSTRAINT chk_anamnese_condicao CHECK  (ds_condicao_corporal IN ('CAQUEXIA','MAGRO','IDEAL','SOBREPESO','OBESO')),
    CONSTRAINT fk_anamnese_consulta  FOREIGN KEY (id_consulta) REFERENCES TB_CLV_CONSULTA (id_consulta)
);

-- Depende: TB_CLV_CONSULTA, TB_CLV_MEDICAMENTO
CREATE TABLE TB_CLV_PRESCRICAO (
    id_prescricao   NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta     NUMBER(19)    NOT NULL,
    id_medicamento  NUMBER(19)    NOT NULL,
    ds_dosagem      VARCHAR2(100) NOT NULL,
    ds_frequencia   VARCHAR2(100),
    nr_duracao_dias NUMBER(10),
    CONSTRAINT fk_prescricao_consulta    FOREIGN KEY (id_consulta)    REFERENCES TB_CLV_CONSULTA   (id_consulta),
    CONSTRAINT fk_prescricao_medicamento FOREIGN KEY (id_medicamento) REFERENCES TB_CLV_MEDICAMENTO (id_medicamento)
);

-- Depende: TB_CLV_CONSULTA, TB_CLV_VETERINARIO (nullable)
CREATE TABLE TB_CLV_EXAME (
    id_exame           NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_exame           VARCHAR2(100)  NOT NULL,
    dt_realizacao      DATE           NOT NULL,
    ds_resultado       VARCHAR2(2000),
    id_consulta        NUMBER(19)     NOT NULL,
    id_vet_solicitante NUMBER(19),                    -- nullable
    CONSTRAINT fk_exame_consulta        FOREIGN KEY (id_consulta)        REFERENCES TB_CLV_CONSULTA    (id_consulta),
    CONSTRAINT fk_exame_vet_solicitante FOREIGN KEY (id_vet_solicitante) REFERENCES TB_CLV_VETERINARIO (id_veterinario)
);

-- Depende: TB_CLV_PET, TB_CLV_TIPO_VACINA, TB_CLV_VETERINARIO, TB_CLV_CONSULTA (nullable)
CREATE TABLE TB_CLV_APLICACAO_VACINA (
    id_aplicacao   NUMBER(19)  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_pet         NUMBER(19)  NOT NULL,
    id_tipo_vacina NUMBER(19)  NOT NULL,
    id_veterinario NUMBER(19)  NOT NULL,
    id_consulta    NUMBER(19),                        -- nullable: vacina pode ser fora de consulta
    dt_aplicacao   DATE        NOT NULL,
    nr_dose        NUMBER(10),
    nr_lote        VARCHAR2(30),
    CONSTRAINT fk_vacina_pet      FOREIGN KEY (id_pet)         REFERENCES TB_CLV_PET        (id_pet),
    CONSTRAINT fk_vacina_tipo     FOREIGN KEY (id_tipo_vacina) REFERENCES TB_CLV_TIPO_VACINA (id_tipo_vacina),
    CONSTRAINT fk_vacina_vet      FOREIGN KEY (id_veterinario) REFERENCES TB_CLV_VETERINARIO (id_veterinario),
    CONSTRAINT fk_vacina_consulta FOREIGN KEY (id_consulta)    REFERENCES TB_CLV_CONSULTA    (id_consulta)
);

-- Depende: TB_CLV_PET, TB_CLV_TIPO_ALERGIA, TB_CLV_CONSULTA (nullable)
CREATE TABLE TB_CLV_ALERGIA_PET (
    id_alergia_pet     NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_pet             NUMBER(19)    NOT NULL,
    id_tipo_alergia    NUMBER(19)    NOT NULL,
    ds_reacao          VARCHAR2(300),
    ds_severidade      VARCHAR2(10),
    dt_identificacao   DATE          NOT NULL,
    id_consulta_origem NUMBER(19),                    -- nullable
    CONSTRAINT chk_alergia_severidade     CHECK  (ds_severidade IN ('LEVE','MODERADA','GRAVE','ANAFILAXIA')),
    CONSTRAINT fk_alergia_pet             FOREIGN KEY (id_pet)              REFERENCES TB_CLV_PET          (id_pet),
    CONSTRAINT fk_alergia_tipo            FOREIGN KEY (id_tipo_alergia)     REFERENCES TB_CLV_TIPO_ALERGIA  (id_tipo_alergia),
    CONSTRAINT fk_alergia_consulta_origem FOREIGN KEY (id_consulta_origem)  REFERENCES TB_CLV_CONSULTA      (id_consulta)
);

-- Depende: TB_CLV_PET, TB_CLV_TIPO_CONDICAO, TB_CLV_CONSULTA (nullable)
CREATE TABLE TB_CLV_CONDICAO_PET (
    id_condicao_pet    NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_pet             NUMBER(19)    NOT NULL,
    id_tipo_condicao   NUMBER(19)    NOT NULL,
    dt_diagnostico     DATE          NOT NULL,
    ds_observacao      VARCHAR2(500),
    fl_ativo           CHAR(1),
    id_consulta_origem NUMBER(19),                    -- nullable
    CONSTRAINT chk_condicao_ativo          CHECK  (fl_ativo IN ('S','N')),
    CONSTRAINT fk_condicao_pet             FOREIGN KEY (id_pet)             REFERENCES TB_CLV_PET           (id_pet),
    CONSTRAINT fk_condicao_tipo            FOREIGN KEY (id_tipo_condicao)   REFERENCES TB_CLV_TIPO_CONDICAO  (id_tipo_condicao),
    CONSTRAINT fk_condicao_consulta_origem FOREIGN KEY (id_consulta_origem) REFERENCES TB_CLV_CONSULTA       (id_consulta)
);

-- Depende: TB_CLV_TIPO_SENSOR, TB_CLV_PET
CREATE TABLE TB_CLV_SENSOR_IOT (
    id_sensor            NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_sensor            VARCHAR2(100) NOT NULL,
    id_tipo_sensor       NUMBER(19)    NOT NULL,
    id_pet               NUMBER(19)    NOT NULL,
    dt_instalacao        DATE          NOT NULL,
    dt_ultima_calibracao DATE,
    fl_ativo             CHAR(1),
    CONSTRAINT chk_sensor_ativo CHECK  (fl_ativo IN ('S','N')),
    CONSTRAINT fk_sensor_tipo   FOREIGN KEY (id_tipo_sensor) REFERENCES TB_CLV_TIPO_SENSOR (id_tipo_sensor),
    CONSTRAINT fk_sensor_pet    FOREIGN KEY (id_pet)         REFERENCES TB_CLV_PET          (id_pet)
);

-- Depende: TB_CLV_SENSOR_IOT
CREATE TABLE TB_CLV_LEITURA_IOT (
    id_leitura NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_sensor  NUMBER(19)    NOT NULL,
    dt_leitura TIMESTAMP     NOT NULL,
    nr_valor   NUMBER(10,4)  NOT NULL,
    CONSTRAINT fk_leitura_sensor FOREIGN KEY (id_sensor) REFERENCES TB_CLV_SENSOR_IOT (id_sensor)
);

-- Depende: TB_CLV_LEITURA_IOT, TB_CLV_CONSULTA (nullable)
CREATE TABLE TB_CLV_ALERTA_IOT (
    id_alerta     NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_leitura    NUMBER(19)    NOT NULL,
    ds_descricao  VARCHAR2(500) NOT NULL,
    ds_severidade VARCHAR2(8),
    dt_alerta     TIMESTAMP     NOT NULL,
    fl_resolvido  CHAR(1),
    id_consulta   NUMBER(19),                         -- nullable
    CONSTRAINT chk_alerta_severidade CHECK  (ds_severidade IN ('BAIXA','MEDIA','ALTA','CRITICA')),
    CONSTRAINT chk_alerta_resolvido  CHECK  (fl_resolvido  IN ('S','N')),
    CONSTRAINT fk_alerta_leitura     FOREIGN KEY (id_leitura)  REFERENCES TB_CLV_LEITURA_IOT (id_leitura),
    CONSTRAINT fk_alerta_consulta    FOREIGN KEY (id_consulta) REFERENCES TB_CLV_CONSULTA     (id_consulta)
);


CREATE TABLE TB_CLV_COLABORADOR (
    id_colaborador  NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_colaborador  VARCHAR2(100)   NOT NULL,
    ds_email        VARCHAR2(150)   NOT NULL,
    ds_senha_hash   VARCHAR2(255)   NOT NULL,
    ds_cargo        VARCHAR2(100)
);

CREATE UNIQUE INDEX UQ_CLV_COLABORADOR_EMAIL ON TB_CLV_COLABORADOR (ds_email);


-- ============================================================
-- FIM DO SCRIPT
-- Total: 25 tabelas criadas
-- ============================================================
