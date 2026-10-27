-- =====================================================================
-- Clyvo Vet / PetFlow - Evidencias do CRUD para a gravacao do video
-- FIAP - DevOps Tools & Cloud Computing - Sprint 4
--
-- SQL Developer conectado ao ACI do banco:
--   Tipo de conexao : Basico
--   Hostname        : rm561940-s4b-db-petflow.brazilsouth.azurecontainer.io
--   Porta           : 1521
--   Nome do servico : PETFLOWDB      <- Servico, NAO SID
--   Usuario         : petflow
--   Senha           : recupere do Key Vault antes de gravar:
--     az keyvault secret show --vault-name kv-petflow-s4b-rm561940 \
--       --name oracle-app-password --query value -o tsv
--
-- COMO USAR
--   Terminal ao lado do SQL Developer. Onde houver "NO TERMINAL", rode o
--   curl ANTES do SELECT que vem abaixo. Uma consulta por vez: Ctrl+Enter.
--
--   Conecte ANTES de comecar a gravar, para a senha nao aparecer na tela.
--
-- As consultas filtram pelo E-MAIL do tutor, nao pelo id: o id depende de
-- quantos tutores ja foram criados (a colecao do Postman, rodada antes no
-- video de Compliance, tambem cria e apaga um).
--
-- A ordem e sempre a mesma: a API altera o dado, o SELECT prova.
-- =====================================================================

-- ---------------------------------------------------------------------
-- PREPARO NO TERMINAL (Git Bash, ANTES de gravar - nada disto vai ao video)
--
--   export MSYS_NO_PATHCONV=1
--   API="https://rm561940-petflow-api-s4b.azurewebsites.net/api"
--   EMAIL="video.sprint4@exemplo.com"
--
--   # Senha do tutor do video: gerada agora, nao fica em arquivo nenhum.
--   SENHA_TUTOR="video-$(openssl rand -hex 4)"
--
--   # Token do master. A senha vem do Key Vault direto para o login.
--   TOKEN=$(curl -s -X POST $API/auth/login \
--     -H "Content-Type: application/json" \
--     -d "{\"email\":\"master@clyvovet.com\",\"senha\":\"$(az keyvault secret show \
--          --vault-name kv-petflow-s4b-rm561940 --name master-password \
--          --query value -o tsv | tr -d '\r')\",\"tipo\":\"COLABORADOR\"}" \
--     | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
--
--   echo ${#TOKEN}          # deve imprimir ~250, nao 0
--   clear                   # limpa a tela antes de gravar
--
-- O token expira em 15 MINUTOS. Se um curl responder 401 no meio da
-- gravacao, isso nao e bug: rode o comando do TOKEN de novo.
-- No maximo 5 logins por minuto por IP: nao repita o TOKEN em sequencia.
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

-- As tabelas criadas pelo Flyway na subida da aplicacao publicada pelo CD.
SELECT COUNT(*) AS total_tabelas FROM user_tables;

-- A carga que o 08_bootstrap.sh criou pela API.
SELECT id_tutor, nm_tutor, ds_email, nr_telefone
  FROM TB_CLV_TUTOR
 ORDER BY id_tutor;


-- =====================================================================
-- CRUD DO TUTOR
-- =====================================================================

-- --- INSERIR ---------------------------------------------------------
-- NO TERMINAL (mostre o 201 e anote o "id" da resposta):
--   curl -i -X POST $API/tutores \
--     -H "Content-Type: application/json" \
--     -d "{\"nome\":\"Tutor Video Sprint 4\",\"email\":\"$EMAIL\",
--          \"telefone\":\"11911112222\",\"senha\":\"$SENHA_TUTOR\",\"clinicaId\":1}"
--
-- POST /api/tutores e rota PUBLICA: nao precisa de token.
-- Depois, guarde o id:   ID=<id da resposta>

SELECT id_tutor, nm_tutor, ds_email, nr_telefone, dt_cadastro
  FROM TB_CLV_TUTOR
 WHERE ds_email = 'video.sprint4@exemplo.com';


-- --- CONSULTAR -------------------------------------------------------
-- NO TERMINAL (200 OK):
--   curl -s -H "Authorization: Bearer $TOKEN" $API/tutores/$ID

SELECT id_tutor, nm_tutor, ds_email, nr_telefone, id_clinica
  FROM TB_CLV_TUTOR
 WHERE ds_email = 'video.sprint4@exemplo.com';


-- --- ATUALIZAR -------------------------------------------------------
-- NO TERMINAL (200 OK):
--   curl -i -X PUT $API/tutores/$ID \
--     -H "Authorization: Bearer $TOKEN" \
--     -H "Content-Type: application/json" \
--     -d "{\"nome\":\"Tutor Video ALTERADO\",\"email\":\"$EMAIL\",
--          \"telefone\":\"11933334444\",\"senha\":\"$SENHA_TUTOR\",\"clinicaId\":1}"
--
-- O PUT reusa o DTO do POST: "senha" e "clinicaId" sao obrigatorios no
-- corpo, alem dos campos alterados. Sem eles a resposta e 422.

SELECT id_tutor, nm_tutor, nr_telefone
  FROM TB_CLV_TUTOR
 WHERE ds_email = 'video.sprint4@exemplo.com';


-- --- DELETAR ---------------------------------------------------------
-- NO TERMINAL (204 No Content):
--   curl -i -X DELETE $API/tutores/$ID -H "Authorization: Bearer $TOKEN"

-- A consulta volta vazia: essa e a evidencia da exclusao.
SELECT id_tutor, nm_tutor
  FROM TB_CLV_TUTOR
 WHERE ds_email = 'video.sprint4@exemplo.com';


-- =====================================================================
-- ESTADO FINAL
-- =====================================================================

-- Sobram so os tutores da carga inicial.
SELECT id_tutor, nm_tutor, ds_email
  FROM TB_CLV_TUTOR
 ORDER BY id_tutor;
