-- =====================================================================
-- Clyvo Vet / PetFlow - Evidencias do CRUD para a gravacao do video
-- FIAP - DevOps Tools & Cloud Computing - Sprint 4
--
-- CRUD nas duas tabelas relacionadas (TUTOR 1:N PET), com a integridade
-- referencial e a exclusao na ordem correta. Cada operacao e feita pela
-- API (curl no terminal) e comprovada por um SELECT aqui.
--
-- SQL Developer conectado ao ACI do banco:
--   Tipo de conexao : Basico
--   Hostname        : rm561940-s4b-db-petflow.brazilsouth.azurecontainer.io
--   Porta           : 1521
--   Nome do servico : PETFLOWDB      <- Servico, NAO SID
--   Usuario         : petflow
--   Senha           : az keyvault secret show --vault-name kv-petflow-s4b-rm561940 \
--                       --name oracle-app-password --query value -o tsv
--
-- COMO USAR
--   Os comandos do terminal estao no roteiro (docs/ROTEIRO_VIDEO.md, Cena 11).
--   Depois de cada comando, clique no SELECT indicado e aperte Ctrl+Enter.
--
--   As consultas filtram pelo E-MAIL do tutor, nao pelo id: o id depende de
--   quantos registros ja foram criados (o Newman do video de Compliance
--   tambem cria e apaga um tutor e um pet).
-- =====================================================================


-- =====================================================================
-- 0. O BANCO E O DA NUVEM
-- =====================================================================

-- O host aparece como "SandboxHost-...": e o container no Azure.
SELECT SYS_CONTEXT('USERENV','SERVER_HOST') AS servidor,
       SYS_CONTEXT('USERENV','CON_NAME')    AS pdb,
       USER                                  AS usuario
  FROM dual;


-- =====================================================================
-- 1. INSERIR o TUTOR   (curl POST /tutores -> 201)
-- =====================================================================
SELECT id_tutor, nm_tutor, ds_email, nr_telefone, dt_cadastro
  FROM TB_CLV_TUTOR
 WHERE ds_email = 'video.sprint4@exemplo.com';


-- =====================================================================
-- 2. INSERIR o PET do tutor   (curl POST /pets -> 201)
-- =====================================================================
-- O pet aponta para o tutor pela FK id_tutor: o relacionamento 1:N.
SELECT p.id_pet, p.nm_pet, p.ds_porte, p.ds_status_pet, t.nm_tutor
  FROM TB_CLV_PET   p
  JOIN TB_CLV_TUTOR t ON t.id_tutor = p.id_tutor
 WHERE t.ds_email = 'video.sprint4@exemplo.com';


-- =====================================================================
-- 3. CONSULTAR   (curl GET /tutores/$ID_TUTOR e GET /pets/$ID_PET -> 200)
-- =====================================================================
-- Os mesmos dados que a API devolveu, lidos do banco.
SELECT t.id_tutor, t.nm_tutor, t.nr_telefone,
       p.id_pet, p.nm_pet, p.ds_porte
  FROM TB_CLV_TUTOR t
  JOIN TB_CLV_PET   p ON p.id_tutor = t.id_tutor
 WHERE t.ds_email = 'video.sprint4@exemplo.com';


-- =====================================================================
-- 4. ATUALIZAR o TUTOR e o PET   (curl PUT /tutores e PUT /pets -> 200)
-- =====================================================================
-- Nome e telefone do tutor, nome e porte do pet: os valores novos.
SELECT t.id_tutor, t.nm_tutor, t.nr_telefone,
       p.id_pet, p.nm_pet, p.ds_porte
  FROM TB_CLV_TUTOR t
  JOIN TB_CLV_PET   p ON p.id_tutor = t.id_tutor
 WHERE t.ds_email = 'video.sprint4@exemplo.com';


-- =====================================================================
-- 5. INTEGRIDADE   (curl DELETE /tutores/$ID_TUTOR -> 409, nunca 500)
-- =====================================================================
-- O tutor tem pet: o banco recusa a exclusao. Nada foi apagado.
SELECT t.id_tutor, t.nm_tutor, p.id_pet, p.nm_pet
  FROM TB_CLV_TUTOR t
  JOIN TB_CLV_PET   p ON p.id_tutor = t.id_tutor
 WHERE t.ds_email = 'video.sprint4@exemplo.com';

-- A chave estrangeira que garante isso:
SELECT constraint_name, table_name, delete_rule
  FROM user_constraints
 WHERE constraint_name = 'FK_PET_TUTOR';


-- =====================================================================
-- 6. DELETAR o PET   (curl DELETE /pets/$ID_PET -> 204)
-- =====================================================================
-- Volta vazia: o pet saiu do banco.
SELECT p.id_pet, p.nm_pet
  FROM TB_CLV_PET   p
  JOIN TB_CLV_TUTOR t ON t.id_tutor = p.id_tutor
 WHERE t.ds_email = 'video.sprint4@exemplo.com';


-- =====================================================================
-- 7. DELETAR o TUTOR   (curl DELETE /tutores/$ID_TUTOR -> 204)
-- =====================================================================
-- Agora funciona: sem pet vinculado, a exclusao e aceita. Volta vazia.
SELECT id_tutor, nm_tutor
  FROM TB_CLV_TUTOR
 WHERE ds_email = 'video.sprint4@exemplo.com';
