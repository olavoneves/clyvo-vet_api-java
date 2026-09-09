-- =====================================================================
-- Clyvo Vet / PetFlow - Evidencias do CRUD para a gravacao do video
-- FIAP - DevOps Tools & Cloud Computing - Sprint 3
--
-- SQL Developer conectado ao ACI do banco:
--   Tipo de conexao : Basico
--   Hostname        : rm561940-db-petflow.brazilsouth.azurecontainer.io
--   Porta           : 1521
--   Nome do servico : PETFLOWDB      <- Servico, NAO SID
--   Usuario         : petflow
--   Senha           : recupere do Key Vault antes de gravar:
--     az keyvault secret show --vault-name kv-petflow-rm561940 \
--       --name oracle-app-password --query value -o tsv
--
-- COMO USAR
--   Terminal ao lado do SQL Developer. Onde houver "NO TERMINAL", rode o
--   curl ANTES do SELECT que vem abaixo. Uma consulta por vez: Ctrl+Enter.
--
--   Conecte ANTES de comecar a gravar, para a senha nao aparecer na tela.
--
-- A ordem e sempre a mesma: a API altera o dado, o SELECT prova.
-- =====================================================================

-- ---------------------------------------------------------------------
-- PREPARO NO TERMINAL (antes de gravar as cenas de CRUD)
--
--   export MSYS_NO_PATHCONV=1
--   APP_FQDN=rm561940-app-petflow.brazilsouth.azurecontainer.io
--   API="http://$APP_FQDN:8080/api"
--
--   TOKEN=$(curl -s -X POST $API/auth/login \
--     -H "Content-Type: application/json" \
--     -d '{"email":"master@clyvovet.com","senha":"master","tipo":"COLABORADOR"}' \
--     | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
--
--   echo ${#TOKEN}          # deve imprimir ~227, nao 0
--
-- O token expira em 15 MINUTOS. Se um curl responder 401 no meio da
-- gravacao, isso nao e bug: rode o comando do TOKEN de novo.
-- ---------------------------------------------------------------------


-- =====================================================================
-- FORMATACAO (rode uma vez, deixa a saida legivel em video)
-- =====================================================================
SET LINESIZE 200
SET PAGESIZE 50


-- =====================================================================
-- ESTADO INICIAL
-- =====================================================================

-- Confirma que a conexao e com o container na Azure, e nao com um banco
-- local: o host aparece como "SandboxHost-...".
SELECT SYS_CONTEXT('USERENV','SERVER_HOST') AS servidor,
       SYS_CONTEXT('USERENV','DB_NAME')     AS banco,
       SYS_CONTEXT('USERENV','CON_NAME')    AS pdb,
       USER                                  AS usuario
  FROM dual;

-- As 37 tabelas criadas pelo Flyway no primeiro boot da aplicacao.
SELECT COUNT(*) AS total_tabelas FROM user_tables;

-- As duas tabelas do CRUD, com a carga que o 07_bootstrap.sh criou pela API.
SELECT id_tutor, nm_tutor, ds_email, nr_telefone, ds_canal_preferencial
  FROM TB_CLV_TUTOR
 ORDER BY id_tutor;

SELECT id_pet, nm_pet, ds_sexo, ds_porte, ds_status_pet, id_tutor
  FROM TB_CLV_PET
 ORDER BY id_pet;

-- O relacionamento 1:N que o requisito 4 do enunciado cobra.
SELECT t.id_tutor, t.nm_tutor, p.id_pet, p.nm_pet, r.nm_raca
  FROM TB_CLV_TUTOR t
  JOIN TB_CLV_PET   p ON p.id_tutor = t.id_tutor
  JOIN TB_CLV_RACA  r ON r.id_raca  = p.id_raca
 ORDER BY t.id_tutor, p.id_pet;


-- =====================================================================
-- TABELA TUTOR - lado 1 do relacionamento
-- =====================================================================

-- --- CREATE ----------------------------------------------------------
-- NO TERMINAL (mostre o 201):
--   curl -i -X POST $API/tutores \
--     -H "Content-Type: application/json" \
--     -d '{"nome":"Carlos Eduardo Souza","email":"carlos.souza@exemplo.com",
--          "telefone":"11966665555","senha":"Clyvo@2026","clinicaId":1}'
--
-- POST /api/tutores e rota PUBLICA: nao precisa de token.

SELECT id_tutor, nm_tutor, ds_email, nr_telefone
  FROM TB_CLV_TUTOR
 ORDER BY id_tutor;

-- >>> Anote o id_tutor do Carlos. As consultas seguintes assumem id = 3.


-- --- READ ------------------------------------------------------------
-- NO TERMINAL (200 OK):
--   curl -s -H "Authorization: Bearer $TOKEN" $API/tutores/3

SELECT id_tutor, nm_tutor, ds_email, dt_cadastro, id_clinica
  FROM TB_CLV_TUTOR
 WHERE id_tutor = 3;


-- --- UPDATE ----------------------------------------------------------
-- NO TERMINAL (200 OK):
--   curl -i -X PUT $API/tutores/3 \
--     -H "Authorization: Bearer $TOKEN" \
--     -H "Content-Type: application/json" \
--     -d '{"nome":"Carlos Eduardo Souza ALTERADO","email":"carlos.souza@exemplo.com",
--          "telefone":"11955554444","senha":"Clyvo@2026","clinicaId":1}'
--
-- O PUT reusa o DTO do POST: "senha" e "clinicaId" sao obrigatorios no
-- corpo, alem dos campos alterados. Sem eles a resposta e 422.

SELECT id_tutor, nm_tutor, nr_telefone
  FROM TB_CLV_TUTOR
 WHERE id_tutor = 3;

-- O DELETE do tutor vem depois: primeiro o pet, por causa da FK.


-- =====================================================================
-- TABELA PET - lado N do relacionamento
-- =====================================================================

-- --- CREATE ----------------------------------------------------------
-- NO TERMINAL (201 Created):
--   curl -i -X POST $API/pets \
--     -H "Authorization: Bearer $TOKEN" \
--     -H "Content-Type: application/json" \
--     -d '{"nome":"Thor","dtNascimento":"2021-06-10","sexo":"M","porte":"GRANDE",
--          "castrado":true,"statusPet":"ATIVO","tutorId":3,"racaId":1}'

SELECT id_pet, nm_pet, ds_sexo, ds_porte, id_tutor
  FROM TB_CLV_PET
 WHERE id_tutor = 3;

-- >>> Anote o id_pet do Thor. As consultas seguintes assumem id = 3.


-- --- READ ------------------------------------------------------------
-- NO TERMINAL (200 OK):
--   curl -s -H "Authorization: Bearer $TOKEN" $API/pets/3

SELECT p.id_pet, p.nm_pet, p.dt_nascimento, p.ds_porte,
       t.nm_tutor, r.nm_raca
  FROM TB_CLV_PET   p
  JOIN TB_CLV_TUTOR t ON t.id_tutor = p.id_tutor
  JOIN TB_CLV_RACA  r ON r.id_raca  = p.id_raca
 WHERE p.id_pet = 3;


-- --- UPDATE ----------------------------------------------------------
-- NO TERMINAL (200 OK):
--   curl -i -X PUT $API/pets/3 \
--     -H "Authorization: Bearer $TOKEN" \
--     -H "Content-Type: application/json" \
--     -d '{"nome":"Thor do Vale","dtNascimento":"2021-06-10","sexo":"M",
--          "porte":"GIGANTE","castrado":true,"statusPet":"ATIVO",
--          "tutorId":3,"racaId":1}'

SELECT id_pet, nm_pet, ds_porte
  FROM TB_CLV_PET
 WHERE id_pet = 3;


-- =====================================================================
-- INTEGRIDADE REFERENCIAL - o 409
-- =====================================================================

-- NO TERMINAL (409 Conflict, NAO 500):
--   curl -i -X DELETE $API/tutores/3 -H "Authorization: Bearer $TOKEN"
--
-- A FK fk_pet_tutor impede orfaos. A aplicacao converte a violacao
-- (ORA-02292) em resposta HTTP legivel, em vez de stack trace.

-- O tutor continua la, e o pet tambem: nada foi apagado.
SELECT t.id_tutor, t.nm_tutor, p.id_pet, p.nm_pet
  FROM TB_CLV_TUTOR t
  JOIN TB_CLV_PET   p ON p.id_tutor = t.id_tutor
 WHERE t.id_tutor = 3;

-- A constraint que produz esse comportamento:
SELECT constraint_name, constraint_type, table_name, delete_rule
  FROM user_constraints
 WHERE constraint_name = 'FK_PET_TUTOR';


-- =====================================================================
-- DELETE na ordem correta - pet primeiro, tutor depois
-- =====================================================================

-- --- DELETE do PET ---------------------------------------------------
-- NO TERMINAL (204 No Content):
--   curl -i -X DELETE $API/pets/3 -H "Authorization: Bearer $TOKEN"

-- A consulta volta vazia: essa e a evidencia da exclusao.
SELECT id_pet, nm_pet FROM TB_CLV_PET WHERE id_pet = 3;

SELECT COUNT(*) AS pets_do_tutor_3 FROM TB_CLV_PET WHERE id_tutor = 3;


-- --- DELETE do TUTOR -------------------------------------------------
-- NO TERMINAL (204 No Content - agora funciona, o pet ja saiu):
--   curl -i -X DELETE $API/tutores/3 -H "Authorization: Bearer $TOKEN"

SELECT id_tutor, nm_tutor FROM TB_CLV_TUTOR WHERE id_tutor = 3;


-- =====================================================================
-- ESTADO FINAL
-- =====================================================================

-- Sobram os dois tutores e os dois pets da carga inicial - as duas linhas
-- com conteudo significativo que o item 5 do enunciado exige.
SELECT t.id_tutor, t.nm_tutor, p.id_pet, p.nm_pet, r.nm_raca
  FROM TB_CLV_TUTOR t
  JOIN TB_CLV_PET   p ON p.id_tutor = t.id_tutor
  JOIN TB_CLV_RACA  r ON r.id_raca  = p.id_raca
 ORDER BY t.id_tutor, p.id_pet;

SELECT (SELECT COUNT(*) FROM TB_CLV_TUTOR) AS tutores,
       (SELECT COUNT(*) FROM TB_CLV_PET)   AS pets
  FROM dual;
