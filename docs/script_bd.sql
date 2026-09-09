-- =====================================================================
--  CLYVO VET / PetFlow — DDL do núcleo da solução
--  FIAP · DevOps Tools & Cloud Computing — Sprint 3
--
--  Oracle XE 21c (PDB XEPDB1), executado em container no Azure
--  Container Instance.
--
--  ESCOPO DESTE ARQUIVO
--  As tabelas do CORE que sustentam o CRUD da entrega, mais as tabelas
--  de domínio de que elas dependem por chave estrangeira. O schema
--  completo tem 37 tabelas e nasce das migrations Flyway em
--  src/main/resources/db/migration (V0 → V13) — este arquivo é o
--  recorte auditável do que a demonstração exercita, gerado a partir
--  daquelas migrations.
--
--  O par do CRUD é TB_CLV_TUTOR 1:N TB_CLV_PET.
--
--  ORDEM DE EXECUÇÃO
--  As tabelas de domínio vêm primeiro porque TB_CLV_PET depende delas.
--  As foreign keys ficam num bloco separado no fim, como na V0_1 — assim
--  a ordem de criação das tabelas deixa de importar.
-- =====================================================================


-- ---------------------------------------------------------------------
--  TB_CLV_CLINICA — a unidade de isolamento multi-tenant
--
--  Toda linha de tutor e de pet pertence a uma clínica. É a raiz do
--  isolamento: um veterinário de uma clínica não enxerga dados de outra.
-- ---------------------------------------------------------------------
CREATE TABLE TB_CLV_CLINICA (
    id_clinica        NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_clinica        VARCHAR2(120) NOT NULL,
    nr_cnpj           VARCHAR2(14)  NOT NULL,
    ds_logradouro     VARCHAR2(200) NOT NULL,
    nr_endereco       VARCHAR2(10),
    ds_bairro         VARCHAR2(80),
    ds_cidade         VARCHAR2(80)  NOT NULL,
    ds_estado         CHAR(2)       NOT NULL,
    nr_cep            VARCHAR2(8),
    nr_telefone       VARCHAR2(20),
    nr_ticket_medio   NUMBER(10,2)  DEFAULT 0,
    CONSTRAINT uk_clinica_cnpj UNIQUE (nr_cnpj)
);

COMMENT ON TABLE  TB_CLV_CLINICA                 IS 'Clínica veterinária cadastrada na plataforma; raiz do isolamento multi-tenant';
COMMENT ON COLUMN TB_CLV_CLINICA.id_clinica      IS 'Chave primária, gerada pelo banco';
COMMENT ON COLUMN TB_CLV_CLINICA.nr_cnpj         IS 'CNPJ apenas com dígitos, sem máscara; único na plataforma';
COMMENT ON COLUMN TB_CLV_CLINICA.nr_ticket_medio IS 'Ticket médio da clínica, usado no cálculo de receita recuperada do painel';


-- ---------------------------------------------------------------------
--  TB_CLV_ESPECIE — espécie animal (canina, felina, ...)
-- ---------------------------------------------------------------------
CREATE TABLE TB_CLV_ESPECIE (
    id_especie NUMBER(19)   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_especie VARCHAR2(60) NOT NULL,
    CONSTRAINT uk_especie_nome UNIQUE (nm_especie)
);

COMMENT ON TABLE  TB_CLV_ESPECIE            IS 'Espécie animal atendida pela clínica';
COMMENT ON COLUMN TB_CLV_ESPECIE.nm_especie IS 'Nome da espécie; único';


-- ---------------------------------------------------------------------
--  TB_CLV_RACA — raça, subordinada à espécie
--
--  O porte padrão e as predisposições da raça alimentam o motor de
--  protocolo clínico: é a partir deles que o sistema decide quais
--  obrigações preventivas valem para cada pet.
-- ---------------------------------------------------------------------
CREATE TABLE TB_CLV_RACA (
    id_raca            NUMBER(19)   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_raca            VARCHAR2(80) NOT NULL,
    id_especie         NUMBER(19)   NOT NULL,
    ds_grupo_raca      VARCHAR2(40),
    ds_porte_padrao    VARCHAR2(10),
    fl_braquicefalico  CHAR(1),
    ds_predisposicoes  VARCHAR2(400),
    CONSTRAINT uk_raca_nome_especie UNIQUE (nm_raca, id_especie),
    CONSTRAINT chk_raca_porte       CHECK  (ds_porte_padrao   IN ('MINI','PEQUENO','MEDIO','GRANDE','GIGANTE')),
    CONSTRAINT chk_raca_braqui      CHECK  (fl_braquicefalico IN ('S','N'))
);

COMMENT ON TABLE  TB_CLV_RACA                   IS 'Raça animal, subordinada a uma espécie';
COMMENT ON COLUMN TB_CLV_RACA.ds_porte_padrao   IS 'Porte típico da raça; herdado pelo pet quando não informado';
COMMENT ON COLUMN TB_CLV_RACA.fl_braquicefalico IS 'S/N — raças braquicefálicas têm protocolo respiratório próprio';
COMMENT ON COLUMN TB_CLV_RACA.ds_predisposicoes IS 'Predisposições clínicas separadas por ponto-e-vírgula; alimenta o motor de protocolo';


-- ---------------------------------------------------------------------
--  TB_CLV_TUTOR — o responsável pelo animal
--
--  Primeira das duas tabelas do CRUD da entrega. É o lado 1 do
--  relacionamento 1:N com TB_CLV_PET.
--
--  A senha é guardada como hash BCrypt: o tutor autentica na API para
--  acompanhar o próprio pet. Nunca há senha em texto claro na tabela.
-- ---------------------------------------------------------------------
CREATE TABLE TB_CLV_TUTOR (
    id_tutor               NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_tutor               VARCHAR2(100) NOT NULL,
    ds_email               VARCHAR2(150) NOT NULL,
    nr_telefone            VARCHAR2(20),
    nr_telefone_emergencia VARCHAR2(20),
    ds_senha_hash          VARCHAR2(255) NOT NULL,
    ds_canal_preferencial  VARCHAR2(10),
    dt_cadastro            DATE,
    id_clinica             NUMBER(19),
    CONSTRAINT uk_tutor_email  UNIQUE (ds_email),
    CONSTRAINT chk_tutor_canal CHECK  (ds_canal_preferencial IN ('APP','WEB','WHATSAPP'))
);

COMMENT ON TABLE  TB_CLV_TUTOR                        IS 'Tutor responsável por um ou mais pets; lado 1 do CRUD da entrega';
COMMENT ON COLUMN TB_CLV_TUTOR.id_tutor               IS 'Chave primária, gerada pelo banco';
COMMENT ON COLUMN TB_CLV_TUTOR.ds_email               IS 'E-mail de login; único na plataforma';
COMMENT ON COLUMN TB_CLV_TUTOR.ds_senha_hash          IS 'Hash BCrypt da senha; nunca a senha em texto claro';
COMMENT ON COLUMN TB_CLV_TUTOR.ds_canal_preferencial  IS 'Por onde o tutor prefere receber lembretes: APP, WEB ou WHATSAPP';
COMMENT ON COLUMN TB_CLV_TUTOR.id_clinica             IS 'Clínica à qual o tutor pertence; chave do isolamento multi-tenant';


-- ---------------------------------------------------------------------
--  TB_CLV_PET — o animal atendido
--
--  Segunda tabela do CRUD e lado N do relacionamento. id_tutor é NOT
--  NULL: um pet não existe sem responsável. Essa obrigatoriedade é o que
--  faz DELETE de tutor com pet vinculado responder 409 em vez de apagar
--  o animal em cascata.
-- ---------------------------------------------------------------------
CREATE TABLE TB_CLV_PET (
    id_pet              NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nm_pet              VARCHAR2(100) NOT NULL,
    dt_nascimento       DATE,
    ds_sexo             CHAR(1),
    nr_microchip        VARCHAR2(30),
    nr_rga              VARCHAR2(30),
    ds_pelagem          VARCHAR2(80),
    ds_porte            VARCHAR2(10),
    fl_castrado         CHAR(1),
    ds_status_pet       VARCHAR2(15),
    ds_observacao_geral VARCHAR2(2000),
    id_tutor            NUMBER(19)    NOT NULL,
    id_raca             NUMBER(19)    NOT NULL,
    id_clinica          NUMBER(19),
    CONSTRAINT uk_pet_microchip UNIQUE (nr_microchip),
    CONSTRAINT chk_pet_sexo     CHECK  (ds_sexo       IN ('M','F','I')),
    CONSTRAINT chk_pet_porte    CHECK  (ds_porte      IN ('MINI','PEQUENO','MEDIO','GRANDE','GIGANTE')),
    CONSTRAINT chk_pet_castrado CHECK  (fl_castrado   IN ('S','N')),
    CONSTRAINT chk_pet_status   CHECK  (ds_status_pet IN ('ATIVO','EM_TRATAMENTO','OBITO','PERDIDO'))
);

COMMENT ON TABLE  TB_CLV_PET                     IS 'Animal atendido pela clínica; lado N do CRUD da entrega';
COMMENT ON COLUMN TB_CLV_PET.id_pet              IS 'Chave primária, gerada pelo banco';
COMMENT ON COLUMN TB_CLV_PET.nr_microchip        IS 'Número do microchip; único quando informado';
COMMENT ON COLUMN TB_CLV_PET.ds_sexo             IS 'M macho, F fêmea, I indefinido';
COMMENT ON COLUMN TB_CLV_PET.fl_castrado         IS 'S/N — influencia o protocolo preventivo aplicável';
COMMENT ON COLUMN TB_CLV_PET.ds_status_pet       IS 'ATIVO, EM_TRATAMENTO, OBITO ou PERDIDO';
COMMENT ON COLUMN TB_CLV_PET.id_tutor            IS 'Tutor responsável; NOT NULL — é o que garante o 409 na exclusão';
COMMENT ON COLUMN TB_CLV_PET.id_raca             IS 'Raça do animal; define porte padrão e predisposições';
COMMENT ON COLUMN TB_CLV_PET.id_clinica          IS 'Clínica à qual o pet pertence; chave do isolamento multi-tenant';


-- =====================================================================
--  CHAVES ESTRANGEIRAS
--
--  Em bloco separado, como na migration V0_1: assim a ordem de criação
--  das tabelas acima deixa de importar.
--
--  Nenhuma FK usa ON DELETE CASCADE. Apagar um tutor que ainda tem pet
--  precisa falhar — a aplicação converte a violação em HTTP 409 com
--  mensagem legível, em vez de apagar o animal silenciosamente.
-- =====================================================================

ALTER TABLE TB_CLV_RACA  ADD CONSTRAINT fk_raca_especie
    FOREIGN KEY (id_especie) REFERENCES TB_CLV_ESPECIE (id_especie);

ALTER TABLE TB_CLV_TUTOR ADD CONSTRAINT fk_tutor_clinica
    FOREIGN KEY (id_clinica) REFERENCES TB_CLV_CLINICA (id_clinica);

ALTER TABLE TB_CLV_PET   ADD CONSTRAINT fk_pet_tutor
    FOREIGN KEY (id_tutor)  REFERENCES TB_CLV_TUTOR (id_tutor);

ALTER TABLE TB_CLV_PET   ADD CONSTRAINT fk_pet_raca
    FOREIGN KEY (id_raca)   REFERENCES TB_CLV_RACA (id_raca);

ALTER TABLE TB_CLV_PET   ADD CONSTRAINT fk_pet_clinica
    FOREIGN KEY (id_clinica) REFERENCES TB_CLV_CLINICA (id_clinica);


-- =====================================================================
--  ÍNDICES
--
--  Oracle cria índice automático para PRIMARY KEY e UNIQUE, mas não para
--  FOREIGN KEY. Os índices abaixo cobrem as buscas que a aplicação faz o
--  tempo todo: "os pets deste tutor" e "os dados desta clínica".
-- =====================================================================

CREATE INDEX ix_pet_tutor     ON TB_CLV_PET   (id_tutor);
CREATE INDEX ix_pet_raca      ON TB_CLV_PET   (id_raca);
CREATE INDEX ix_pet_clinica   ON TB_CLV_PET   (id_clinica);
CREATE INDEX ix_tutor_clinica ON TB_CLV_TUTOR (id_clinica);
CREATE INDEX ix_raca_especie  ON TB_CLV_RACA  (id_especie);


-- =====================================================================
--  CARGA MÍNIMA DE DEMONSTRAÇÃO
--
--  Duas linhas com conteúdo significativo em cada tabela do CRUD, como
--  exige o item 5 do enunciado. Os dados são coerentes entre si: cada
--  pet aponta para um tutor real e para uma raça compatível.
--
--  As senhas são hashes BCrypt de 'Clyvo@2026' — base de demonstração,
--  não de produção. O hash é público de propósito.
--
--  Em nuvem esta carga é feita pelo script scripts/07_bootstrap.sh, que
--  chama a própria API: assim o vídeo evidencia a integração App ↔ Banco
--  em vez de um INSERT direto.
-- =====================================================================

INSERT INTO TB_CLV_CLINICA (nm_clinica, nr_cnpj, ds_logradouro, nr_endereco,
                            ds_bairro, ds_cidade, ds_estado, nr_cep, nr_telefone)
VALUES ('Clinica PetFlow Central', '12345678000199', 'Av Paulista', '1000',
        'Bela Vista', 'Sao Paulo', 'SP', '01310100', '1133334444');

INSERT INTO TB_CLV_ESPECIE (nm_especie) VALUES ('CANINA');
INSERT INTO TB_CLV_ESPECIE (nm_especie) VALUES ('FELINA');

INSERT INTO TB_CLV_RACA (nm_raca, id_especie, ds_grupo_raca, ds_porte_padrao,
                         fl_braquicefalico, ds_predisposicoes)
VALUES ('Golden Retriever',
        (SELECT id_especie FROM TB_CLV_ESPECIE WHERE nm_especie = 'CANINA'),
        'RETRIEVER', 'GRANDE', 'N', 'DISPLASIA_COXOFEMORAL;CARDIOPATIA');

INSERT INTO TB_CLV_RACA (nm_raca, id_especie, ds_grupo_raca, ds_porte_padrao,
                         fl_braquicefalico, ds_predisposicoes)
VALUES ('Siames',
        (SELECT id_especie FROM TB_CLV_ESPECIE WHERE nm_especie = 'FELINA'),
        'ORIENTAL', 'PEQUENO', 'N', 'DOENCA_RENAL_CRONICA');

-- Tutores — item 5: duas linhas com conteúdo significativo
INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                          ds_canal_preferencial, dt_cadastro, id_clinica)
VALUES ('Maria Aparecida Silva', 'maria.silva@exemplo.com', '11988887777',
        '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
        'APP', SYSDATE,
        (SELECT id_clinica FROM TB_CLV_CLINICA WHERE nr_cnpj = '12345678000199'));

INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                          ds_canal_preferencial, dt_cadastro, id_clinica)
VALUES ('Joao Pedro Nascimento', 'joao.nascimento@exemplo.com', '11977776666',
        '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
        'WHATSAPP', SYSDATE,
        (SELECT id_clinica FROM TB_CLV_CLINICA WHERE nr_cnpj = '12345678000199'));

-- Pets — item 5: duas linhas, cada uma vinculada a um tutor real
INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip, ds_porte,
                        fl_castrado, ds_status_pet, id_tutor, id_raca, id_clinica)
VALUES ('Luna', DATE '2022-03-15', 'F', 'CHIP000000000001', 'GRANDE', 'S', 'ATIVO',
        (SELECT id_tutor  FROM TB_CLV_TUTOR   WHERE ds_email = 'maria.silva@exemplo.com'),
        (SELECT id_raca   FROM TB_CLV_RACA    WHERE nm_raca  = 'Golden Retriever'),
        (SELECT id_clinica FROM TB_CLV_CLINICA WHERE nr_cnpj = '12345678000199'));

INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip, ds_porte,
                        fl_castrado, ds_status_pet, id_tutor, id_raca, id_clinica)
VALUES ('Mingau', DATE '2023-08-02', 'M', 'CHIP000000000002', 'PEQUENO', 'N', 'ATIVO',
        (SELECT id_tutor  FROM TB_CLV_TUTOR   WHERE ds_email = 'joao.nascimento@exemplo.com'),
        (SELECT id_raca   FROM TB_CLV_RACA    WHERE nm_raca  = 'Siames'),
        (SELECT id_clinica FROM TB_CLV_CLINICA WHERE nr_cnpj = '12345678000199'));

COMMIT;


-- =====================================================================
--  CONSULTAS DE EVIDÊNCIA
--
--  O item 9.3 do enunciado exige demonstrar cada operação do CRUD por
--  SELECT dentro do banco. Estas são as consultas usadas na gravação.
-- =====================================================================

-- Tutores cadastrados
SELECT id_tutor, nm_tutor, ds_email, nr_telefone, ds_canal_preferencial
  FROM TB_CLV_TUTOR
 ORDER BY id_tutor;

-- Pets cadastrados
SELECT id_pet, nm_pet, ds_sexo, ds_porte, ds_status_pet, id_tutor
  FROM TB_CLV_PET
 ORDER BY id_pet;

-- O relacionamento 1:N, que é o objeto do requisito 4
SELECT t.id_tutor,
       t.nm_tutor,
       p.id_pet,
       p.nm_pet,
       r.nm_raca,
       e.nm_especie
  FROM TB_CLV_TUTOR   t
  JOIN TB_CLV_PET     p ON p.id_tutor   = t.id_tutor
  JOIN TB_CLV_RACA    r ON r.id_raca    = p.id_raca
  JOIN TB_CLV_ESPECIE e ON e.id_especie = r.id_especie
 ORDER BY t.id_tutor, p.id_pet;
