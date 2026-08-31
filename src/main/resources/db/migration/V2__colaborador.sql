-- V2: cria tabela de colaboradores internos (sem FKs para outras entidades)
CREATE TABLE TB_CLV_COLABORADOR (
    id_colaborador  NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_colaborador  VARCHAR2(100)   NOT NULL,
    ds_email        VARCHAR2(150)   NOT NULL,
    ds_senha_hash   VARCHAR2(255)   NOT NULL,
    ds_cargo        VARCHAR2(100)
);

CREATE UNIQUE INDEX UQ_CLV_COLABORADOR_EMAIL ON TB_CLV_COLABORADOR (ds_email);
