-- ============================================================
-- MIGRATION V3 - Multi-tenancy: id_clinica em PET, TUTOR e COLABORADOR
-- ============================================================
-- TB_CLV_VETERINARIO ja possuia id_clinica desde o modelo inicial.
-- Esta migration estende o tenant para os demais perfis e para o pet.
--
-- Estrategia em 3 tempos, obrigatoria em tabela com dados:
--   1. adiciona a coluna como NULL
--   2. faz o backfill (pet herda a clinica do tutor)
--   3. so entao aplica NOT NULL + FK
--
-- Se o banco ainda nao tiver nenhuma clinica cadastrada e existirem
-- linhas orfas, o bloco PL/SQL cria uma clinica de migracao para que
-- o NOT NULL possa ser aplicado. Renomeie-a depois pelos dados reais.
-- ============================================================


-- ============================================================
-- PASSO 1: colunas nullable
-- ============================================================
ALTER TABLE TB_CLV_TUTOR       ADD (id_clinica NUMBER(19));
ALTER TABLE TB_CLV_COLABORADOR ADD (id_clinica NUMBER(19));
ALTER TABLE TB_CLV_PET         ADD (id_clinica NUMBER(19));


-- ============================================================
-- PASSO 2: backfill
-- ============================================================
DECLARE
    v_id_clinica NUMBER(19);
    v_pendentes  NUMBER;
BEGIN
    SELECT (SELECT COUNT(*) FROM TB_CLV_TUTOR       WHERE id_clinica IS NULL)
         + (SELECT COUNT(*) FROM TB_CLV_COLABORADOR WHERE id_clinica IS NULL)
         + (SELECT COUNT(*) FROM TB_CLV_PET         WHERE id_clinica IS NULL)
      INTO v_pendentes
      FROM dual;

    IF v_pendentes = 0 THEN
        RETURN;
    END IF;

    SELECT MIN(id_clinica) INTO v_id_clinica FROM TB_CLV_CLINICA;

    IF v_id_clinica IS NULL THEN
        INSERT INTO TB_CLV_CLINICA (nm_clinica, ds_cnpj, ds_logradouro, nm_cidade, sg_estado)
        VALUES ('CLINICA PADRAO (MIGRACAO V3)', '00000000000000', 'NAO INFORMADO', 'NAO INFORMADO', 'SP')
        RETURNING id_clinica INTO v_id_clinica;
    END IF;

    UPDATE TB_CLV_TUTOR       SET id_clinica = v_id_clinica WHERE id_clinica IS NULL;
    UPDATE TB_CLV_COLABORADOR SET id_clinica = v_id_clinica WHERE id_clinica IS NULL;

    -- o pet pertence a clinica do seu tutor; so cai no default se o tutor nao resolver
    UPDATE TB_CLV_PET p
       SET p.id_clinica = (SELECT t.id_clinica FROM TB_CLV_TUTOR t WHERE t.id_tutor = p.id_tutor)
     WHERE p.id_clinica IS NULL;

    UPDATE TB_CLV_PET SET id_clinica = v_id_clinica WHERE id_clinica IS NULL;
END;
/


-- ============================================================
-- PASSO 3: NOT NULL + FK + indice
-- ============================================================
ALTER TABLE TB_CLV_TUTOR       MODIFY id_clinica NOT NULL;
ALTER TABLE TB_CLV_COLABORADOR MODIFY id_clinica NOT NULL;
ALTER TABLE TB_CLV_PET         MODIFY id_clinica NOT NULL;

ALTER TABLE TB_CLV_TUTOR
    ADD CONSTRAINT fk_tutor_clinica
    FOREIGN KEY (id_clinica) REFERENCES TB_CLV_CLINICA (id_clinica);

ALTER TABLE TB_CLV_COLABORADOR
    ADD CONSTRAINT fk_colaborador_clinica
    FOREIGN KEY (id_clinica) REFERENCES TB_CLV_CLINICA (id_clinica);

ALTER TABLE TB_CLV_PET
    ADD CONSTRAINT fk_pet_clinica
    FOREIGN KEY (id_clinica) REFERENCES TB_CLV_CLINICA (id_clinica);

-- toda query da aplicacao passa a filtrar por id_clinica
CREATE INDEX ix_tutor_clinica       ON TB_CLV_TUTOR       (id_clinica);
CREATE INDEX ix_colaborador_clinica ON TB_CLV_COLABORADOR (id_clinica);
CREATE INDEX ix_pet_clinica         ON TB_CLV_PET         (id_clinica);
CREATE INDEX ix_veterinario_clinica ON TB_CLV_VETERINARIO (id_clinica);
