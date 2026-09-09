# Roteiro do vídeo — Sprint 3 · DevOps Tools & Cloud Computing

**Vale 80 dos 100 pontos.** O outro 20 é o diagrama de arquitetura.

> O enunciado é literal: *"O vídeo é sua prova entregue, deve estar impecável para
> uma nota máxima."*

---

## Regras que zeram ou descontam

| Regra | Peso |
|---|---|
| Entrega em localhost | **zero** |
| Professor sem acesso ao repositório ou ao vídeo | **zero** |
| Mínimo 720p | -30 se abaixo |
| Áudio claro, **explicação por voz** | -30 se ausente |
| **Sem legendas** nesta entrega | proibido |
| **Sem cortes** ao evidenciar testes e persistência | — |
| Evidência de cada operação do CRUD **no banco, por SELECT** | -30 se faltar |

Duas leituras que valem repetir:

**"Sem cortes"** vale para os trechos de teste e persistência. Entre uma etapa e
outra pode cortar; **durante** a demonstração do CRUD, não.

**"Sem legendas"** é proibição explícita nesta entrega. Não ative legenda
automática do YouTube antes de publicar.

---

## Antes de gravar

- [ ] `az login` feito e assinatura correta selecionada
- [ ] Docker Desktop em execução
- [ ] Resource Group **apagado** — a gravação começa do zero
- [ ] **Key Vault purgado** — apagar o RG deixa o cofre em *soft delete* por 90
      dias, e o `03_key-vault.sh` aborta se o nome ainda estiver preso:

      ```bash
      az keyvault list-deleted --query "[?name=='kv-petflow-rm561940'].name" -o tsv
      az keyvault purge --name kv-petflow-rm561940      # se o comando acima retornar algo
      ```
- [ ] Terminal com fonte grande (14pt+), tema de alto contraste
- [ ] Notificações do sistema desligadas
- [ ] Microfone testado
- [ ] Uma aba do navegador aberta no Portal do Azure, autenticada
- [ ] Este roteiro aberto numa segunda tela
- [ ] **SQL Developer aberto**, com a janela de nova conexão pronta — mas **ainda
      não conectado**: o banco só existe depois da Cena 7
- [ ] [`EVIDENCIAS_VIDEO.sql`](EVIDENCIAS_VIDEO.sql) aberto numa aba do SQL Developer

### Conexão do SQL Developer

| Campo | Valor |
|---|---|
| Tipo de conexão | Básico |
| Hostname | `rm561940-db-petflow.brazilsouth.azurecontainer.io` |
| Porta | `1521` |
| **Nome do serviço** | `PETFLOWDB` — serviço, **não** SID |
| Usuário | `petflow` |
| Senha | do Key Vault (comando abaixo) |

```bash
az keyvault secret show --vault-name kv-petflow-rm561940   --name oracle-app-password --query value -o tsv
```

> **Não rode nada disso antes de gravar.** O cofre nasce na Cena 5 e o banco na
> Cena 7 — antes disso o comando falha e a conexão não tem destino.
>
> **O momento é a Cena 7:** enquanto o `05_aci-db.sh` espera o Oracle subir
> (~50 s), você recupera a senha e conecta o SQL Developer. Na Cena 11 ele já
> está pronto.

### ⚠️ Dois cuidados durante a gravação

Não são comandos a executar — são coisas a **evitar** enquanto grava.

**Cuidado com o log do BANCO — o da aplicação é seguro.**

| Container | Tem segredo no log? | |
|---|---|---|
| `rm561940-aci-app` | não | ✅ pode mostrar à vontade |
| `rm561940-aci-db` | **sim** | ⚠️ filtre ou evite |

O entrypoint da imagem Oracle ecoa `ALTER USER SYS IDENTIFIED BY "..."` em texto
claro, logo depois de `Database opened`. Descoberto no spike de 25/08.
`--secure-environment-variables` protege o `az container show`, mas **não** o log.

Você não precisa do log do banco em nenhuma cena: o `05_aci-db.sh` já imprime
`Oracle pronto após aproximadamente 50 segundos`, e isso basta como evidência.

Se quiser mostrar assim mesmo, a senha aparece no **começo** — então o fim é
seguro:

```bash
az container logs -g rg-petflow-rm561940 -n rm561940-aci-db | tail -20
az container logs -g rg-petflow-rm561940 -n rm561940-aci-db | grep -v "ALTER USER"
```

**Não reinicie o ACI do banco durante a gravação.** Não há volume: reiniciar zera
os dados e você perde tudo que demonstrou até ali.

---

## Roteiro

Ordem derivada dos itens 9.2 e 9.3 do enunciado. O item 9.2 exige que o vídeo
siga **exatamente** os passos do README — e ele segue.

### Cena 1 · Abertura (~1 min)

Diga em voz:
- Nome e RM dos integrantes
- Que a entrega é a **Opção 1: ACR + ACI**, com containerização completa
- O que a aplicação faz, em duas frases

Mostre `docs/arquitetura_sprint3.png` na tela e percorra o caminho:

1. Do ambiente local: build das imagens e push para o ACR (badges ① e ②)
2. Dentro do Resource Group: ACR guarda as duas imagens, Key Vault guarda os
   três segredos
3. Setas laranja: cada ACI puxa sua imagem do ACR e recebe os segredos do cofre
4. Seta vermelha entre os dois ACIs: **JDBC pelo FQDN público** — são container
   groups separados, não há rede interna
5. Seta verde: o Flyway aplica as 15 migrations e cria as 37 tabelas
6. Seta roxa à direita: o usuário final chega pela internet na porta 8080

> Isto também alimenta o item 9.1. Explique as três decisões: dois container
> groups separados, container da aplicação não-root, e banco sem volume.

### Cena 2 · Clone do repositório (~1 min) 🔵 obrigatório começar assim

```bash
git clone https://github.com/olavoneves/clyvo-vet_api-java.git
cd clyvo-vet_api-java
git checkout devops/sprint3
```

Abra o `README.md` e mostre que o roteiro a seguir está escrito ali.

### Cena 3 · Variáveis e ausência de segredos (~1 min)

```bash
cat scripts/00_variables.sh
```

Diga em voz: **nenhuma senha neste arquivo**. As três são geradas em runtime no
passo seguinte e guardadas no Key Vault.

### Cena 4 · Infraestrutura base (~2 min)

```bash
bash scripts/01_resource-group.sh
bash scripts/02_acr.sh
```

Mostre no Portal o Resource Group e o ACR recém-criados.

### Cena 5 · Segredos (~1 min)

```bash
bash scripts/03_key-vault.sh
```

Aponte a saída: os **nomes** dos três segredos aparecem, os **valores** nunca.

### Cena 6 · Build e push (~5 a 10 min)

```bash
bash scripts/04_build-push.sh
```

Este é o trecho mais longo: a imagem do Oracle tem ~755 MB. **Pode cortar aqui** —
não é evidência de teste nem de persistência.

Ao final o próprio script já imprime as duas imagens e a arquitetura. Se quiser
repetir na tela:

```bash
az acr repository list --name acrpetflowrm561940 --output table
```

Deve listar **as duas** 🔵:

```
rm561940-app-petflow
rm561940-db-petflow
```

E a verificação de arquitetura do script imprimindo `amd64` para ambas.

### Cena 7 · ACI do banco (~3 min)

```bash
bash scripts/05_aci-db.sh
```

O script faz poll até `DATABASE IS READY TO USE!` — no ensaio levou **50 s**.
Enquanto espera, explique: 2 vCPU / 4 GB, sem volume, imagem vinda do ACR.

> **Aproveite esta janela para conectar o SQL Developer.** Numa segunda tela,
> fora do enquadramento:
>
> ```bash
> az keyvault secret show --vault-name kv-petflow-rm561940 >   --name oracle-app-password --query value -o tsv
> ```
>
> Cole a senha na conexão descrita no topo deste roteiro. Se conectar aqui, na
> Cena 11 basta trazer a janela para a tela — sem senha aparecendo em vídeo.

### Cena 8 · ACI da aplicação (~3 min)

```bash
bash scripts/06_aci-app.sh
```

Chame atenção para a URL JDBC impressa: aponta para o **FQDN público** do banco,
não para `localhost`. É o que prova que são dois container groups distintos.

No log, mostre o Flyway aplicando as 15 migrations — **este log é seguro**, a
aplicação não ecoa segredo nenhum:

```bash
az container logs -g rg-petflow-rm561940 -n rm561940-aci-app | grep -i "migrat\|Started"
```

### Cena 9 · Prova de não-root (~1 min) 🔵 requisito 8.2

**De um terminal interativo** — `az container exec` exige TTY real:

```bash
az container exec \
  --resource-group rg-petflow-rm561940 \
  --name rm561940-aci-app \
  --exec-command "id"
```

Saída esperada: `uid=999(appuser)`. Diga em voz que **não há `uid=0(root)`**.

### Cena 10 · Bootstrap (~2 min)

```bash
bash scripts/07_bootstrap.sh
```

Explique por que este passo existe: o Flyway cria o schema, mas o banco nasce sem
dados — e o usuário master só é criado se já houver uma clínica.

Ao final o script cria **2 tutores e 2 pets** com dados reais, atendendo ao item 5
do enunciado.

### Cena 11 · SELECT inicial no banco (~2 min) 🔵 daqui em diante, SEM CORTES

Traga o **SQL Developer** para a tela, já conectado, com o
[`EVIDENCIAS_VIDEO.sql`](EVIDENCIAS_VIDEO.sql) aberto.

Diga em voz que a conexão é com o **FQDN público do ACI**, pela porta 1521 — não
é um banco local. A primeira consulta do arquivo prova isso:

```sql
SELECT SYS_CONTEXT('USERENV','SERVER_HOST') AS servidor,
       SYS_CONTEXT('USERENV','CON_NAME')    AS pdb,
       USER                                  AS usuario
  FROM dual;
```

O servidor aparece como `SandboxHost-...` — é o host do ACI.

Depois rode, uma por vez (Ctrl+Enter):

- `SELECT COUNT(*) FROM user_tables` → **37 tabelas**, criadas pelo Flyway
- Os dois `SELECT` de `TB_CLV_TUTOR` e `TB_CLV_PET` → a carga do bootstrap
- O `JOIN` das duas tabelas → o relacionamento 1:N do requisito 4

**Deixe o SQL Developer aberto.** Daqui até a Cena 15 a tela se divide: `curl` de
um lado, SQL Developer do outro.

### Cena 12 · CRUD de TUTOR (~5 min) 🔵 item 9.3 — cada operação com SELECT

Trabalhe com **duas janelas lado a lado**: `curl` numa, SQL Developer na outra.
Todos os comandos e consultas desta cena e das seguintes estao em
[`EVIDENCIAS_VIDEO.sql`](EVIDENCIAS_VIDEO.sql), na ordem de execucao — ele e a
fonte unica, este roteiro so narra.

Autentique primeiro:

```bash
FQDN=$(az container show -g rg-petflow-rm561940 -n rm561940-aci-app --query ipAddress.fqdn -o tsv)
API="http://$FQDN:8080/api"

TOKEN=$(curl -s -X POST $API/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"master@clyvovet.com","senha":"master","tipo":"COLABORADOR"}' \
  | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

**INSERÇÃO** — cria e mostra no banco:

```bash
curl -X POST $API/tutores -H "Content-Type: application/json" \
  -d '{"nome":"Carlos Eduardo Souza","email":"carlos.souza@exemplo.com",
       "telefone":"11966665555","senha":"Clyvo@2026","clinicaId":1}'
```

```sql
SELECT id_tutor, nm_tutor, ds_email FROM TB_CLV_TUTOR ORDER BY id_tutor;
```

**ATUALIZAÇÃO** — altera e mostra no banco:

```bash
curl -X PUT $API/tutores/<ID> -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Carlos Eduardo Souza ALTERADO","email":"carlos.souza@exemplo.com",
       "telefone":"11955554444","senha":"Clyvo@2026","clinicaId":1}'
```

```sql
SELECT id_tutor, nm_tutor, nr_telefone FROM TB_CLV_TUTOR WHERE id_tutor = <ID>;
```

**CONSULTA:**

```bash
curl -H "Authorization: Bearer $TOKEN" $API/tutores/<ID>
```

> O `PUT` reusa o DTO do `POST`: `senha` e `clinicaId` são obrigatórios no corpo.
> Sem eles a resposta é **422**, não 200.

### Cena 13 · CRUD de PET (~5 min) 🔵 a segunda tabela

Mesma mecânica, agora em `/api/pets`. É o que satisfaz o requisito de **duas
tabelas relacionadas** (-20 se usar só uma).

**INSERÇÃO:**

```bash
curl -X POST $API/pets -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Thor","dtNascimento":"2021-06-10","sexo":"M","porte":"GRANDE",
       "castrado":true,"statusPet":"ATIVO","tutorId":<ID_TUTOR>,"racaId":1}'
```

```sql
SELECT id_pet, nm_pet, ds_porte, id_tutor FROM TB_CLV_PET ORDER BY id_pet;
```

**ATUALIZAÇÃO** → `SELECT`
**CONSULTA** → `curl GET`

**EXCLUSÃO** — apaga e mostra a ausência no banco:

```bash
curl -X DELETE $API/pets/<ID_PET> -H "Authorization: Bearer $TOKEN"
```

```sql
SELECT id_pet, nm_pet FROM TB_CLV_PET ORDER BY id_pet;
```

### Cena 14 · Integridade referencial (~2 min)

Mostre o relacionamento sendo respeitado — tentar apagar tutor com pet vinculado:

```bash
curl -i -X DELETE $API/tutores/<ID_TUTOR_COM_PET> -H "Authorization: Bearer $TOKEN"
```

Resposta: **409 Conflict**, não 500. Explique em voz: a FK `fk_pet_tutor` impede
órfãos, e a aplicação converte a violação em resposta legível.

```sql
SELECT t.nm_tutor, p.nm_pet
  FROM TB_CLV_TUTOR t JOIN TB_CLV_PET p ON p.id_tutor = t.id_tutor
 ORDER BY t.id_tutor;
```

### Cena 15 · Exclusão na ordem correta (~2 min)

Pet primeiro, tutor depois — cada passo com `SELECT`:

```bash
curl -X DELETE $API/pets/<ID_PET>       -H "Authorization: Bearer $TOKEN"   # 204
curl -X DELETE $API/tutores/<ID_TUTOR>  -H "Authorization: Bearer $TOKEN"   # 204
```

```sql
SELECT COUNT(*) FROM TB_CLV_PET   WHERE id_pet   = <ID_PET>;    -- 0
SELECT COUNT(*) FROM TB_CLV_TUTOR WHERE id_tutor = <ID_TUTOR>;  -- 0
```

### Cena 16 · Recursos no Portal (~1 min)

Mostre no Portal do Azure, dentro do Resource Group:
- ACR com as duas imagens
- Os **dois** ACIs em `Running`
- Key Vault com os três segredos (só os nomes, nunca os valores)

E, de volta ao terminal, a prova de que os segredos não vazam 🔵:

```bash
az container show   --resource-group rg-petflow-rm561940   --name rm561940-aci-app   --query "containers[0].environmentVariables[].{nome:name, valor:value}"   --output table
```

`SPRING_DATASOURCE_PASSWORD` e `JWT_SECRET` aparecem com a coluna **valor
vazia** — é o efeito do `--secure-environment-variables`.

### Cena 17 · Encerramento (~1 min)

Retome: containerização completa, recursos por CLI, CRUD nas duas tabelas
evidenciado no banco, aplicação não-root, nenhum segredo exposto.

**Não execute o `99_cleanup.sh` no vídeo.** O professor precisa conseguir acessar
a solução para corrigir.

---

## Duração estimada

| Bloco | Tempo |
|---|---|
| Cenas 1–5 (abertura e infra) | ~6 min |
| Cena 6 (build e push) | ~5–10 min · **pode cortar** |
| Cenas 7–10 (deploy e bootstrap) | ~9 min |
| Cenas 11–15 (CRUD) | ~16 min · **sem cortes** |
| Cenas 16–17 (portal e fecho) | ~2 min |

Total: **35 a 45 minutos**. O enunciado não impõe limite, mas pede para evitar
excesso.

---

## Conferência final antes de publicar

- [ ] 720p ou superior
- [ ] Áudio audível do início ao fim
- [ ] **Nenhuma legenda** ativada
- [ ] Nenhuma senha visível em nenhum frame
- [ ] Começa com `git clone`
- [ ] Cada operação do CRUD tem seu `SELECT` correspondente
- [ ] Sem cortes nas cenas 11 a 15
- [ ] Publicado como **não listado** e o link testado numa janela anônima
