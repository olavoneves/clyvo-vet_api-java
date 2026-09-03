-- ============================================================
-- MIGRATION V1 - Autenticação JWT: campos de Veterinário + tabela de RefreshToken
-- ============================================================
-- Responsável execução: Analista / DBA
-- Impacto: 1 ALTER TABLE + 1 CREATE TABLE
-- Banco: Oracle (TB_CLV_*)
-- Dependências: nenhuma (TB_CLV_VETERINARIO já existe)
-- ============================================================
--
-- CONTEXTO
-- --------
-- TB_CLV_TUTOR já possui ds_email e ds_senha_hash desde o modelo inicial.
-- TB_CLV_VETERINARIO precisará dos mesmos campos para suportar login JWT.
-- A aplicação Java usará BCrypt para armazenar senhas.
--
-- ATENÇÃO ANTES DE EXECUTAR
-- -------------------------
-- 1. Fazer backup da tabela TB_CLV_VETERINARIO antes de iniciar.
-- 2. As colunas são adicionadas como NULL primeiro.
-- 3. Após adicionar, popular os dados (passo 1b) ANTES de aplicar NOT NULL.
-- 4. Se a tabela estiver vazia pode executar o script inteiro de uma vez.
-- 5. Todos os valores de ds_senha_hash devem ser BCrypt ($2a$10$...).
--    Não inserir senhas em texto plano.
-- ============================================================


-- ============================================================
-- PASSO 1a: Adicionar colunas em TB_CLV_VETERINARIO (nullable)
-- ============================================================
ALTER TABLE TB_CLV_VETERINARIO ADD (
    ds_email      VARCHAR2(150),
    ds_senha_hash VARCHAR2(255)
);

-- ============================================================
-- PASSO 1b: Popular colunas nos registros existentes
-- ============================================================
-- Executar UPDATE para cada veterinário existente antes do passo 1c.
-- Exemplo (substituir pelos valores reais):
--
-- UPDATE TB_CLV_VETERINARIO
--    SET ds_email      = 'joao.silva@clinica.com',
--        ds_senha_hash = '$2a$10$HASH_BCRYPT_AQUI'
--  WHERE id_veterinario = 1;
--
-- COMMIT;

-- ============================================================
-- PASSO 1c: Aplicar constraints após popular os dados
-- ============================================================
ALTER TABLE TB_CLV_VETERINARIO MODIFY ds_email      NOT NULL;
ALTER TABLE TB_CLV_VETERINARIO MODIFY ds_senha_hash NOT NULL;

ALTER TABLE TB_CLV_VETERINARIO
    ADD CONSTRAINT uk_veterinario_email UNIQUE (ds_email);


-- ============================================================
-- PASSO 2: Criar tabela TB_CLV_REFRESH_TOKEN
-- ============================================================
-- Armazena refresh tokens emitidos pelo endpoint POST /auth/login.
-- fl_revogado: 'N' = ativo, 'S' = revogado (padrão da aplicação).
-- ============================================================
CREATE TABLE TB_CLV_REFRESH_TOKEN (
    id_refresh_token NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ds_token         VARCHAR2(512)  NOT NULL,
    ds_tipo_usuario  VARCHAR2(15)   NOT NULL,
    nr_id_usuario    NUMBER(19)     NOT NULL,
    dt_expiracao     TIMESTAMP      NOT NULL,
    fl_revogado      CHAR(1)        DEFAULT 'N' NOT NULL,
    dt_criacao       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_refresh_token    UNIQUE  (ds_token),
    CONSTRAINT ck_tipo_usuario_rt  CHECK   (ds_tipo_usuario IN ('TUTOR', 'VETERINARIO')),
    CONSTRAINT ck_fl_revogado_rt   CHECK   (fl_revogado IN ('S', 'N'))
);

-- ============================================================
-- PASSO 3: Observação sobre TB_CLV_TUTOR
-- ============================================================
-- TB_CLV_TUTOR já possui ds_email e ds_senha_hash desde o modelo inicial.
-- Verificar se os valores existentes em ds_senha_hash estão em BCrypt.
-- Se estiverem como texto plano, será necessário re-hashear antes de
-- ativar o novo sistema de autenticação.
--
-- ROLLBACK (se necessário antes de aplicar NOT NULL):
-- ALTER TABLE TB_CLV_VETERINARIO DROP COLUMN ds_email;
-- ALTER TABLE TB_CLV_VETERINARIO DROP COLUMN ds_senha_hash;
-- DROP TABLE TB_CLV_REFRESH_TOKEN;
-- ============================================================
