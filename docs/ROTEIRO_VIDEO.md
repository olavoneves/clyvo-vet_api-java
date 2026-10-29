# Roteiro do vídeo — Sprint 4 · DevOps Tools & Cloud Computing

**Vale 80 dos 100 pontos.** Os outros 20 são o diagrama de arquitetura com o fluxo CI/CD,
que fica no README.

> O vídeo é gravado na **mesma sessão** do vídeo de Compliance
> ([`compliance/ROTEIRO_VIDEO_TESTES.md`](compliance/ROTEIRO_VIDEO_TESTES.md)): a infra é
> ligada uma vez só. Grave o de Compliance primeiro, porque é mais curto.

---

## Regras que zeram ou descontam

| Regra | Peso |
|---|---|
| Entrega em localhost | **zero** |
| Professor sem acesso ao Azure DevOps ou ao vídeo | **zero** |
| Pipeline de CI ou de CD não funcionando | **zero** |
| Mínimo 720p | -30 se abaixo |
| Áudio claro, **explicação por voz** | -30 se ausente |
| **Sem legendas** nesta entrega | proibido |
| CI/CD que não dispara sozinho com mudança de código na branch principal | -30 |
| Alterar o README em vez do código | -20 |
| Não explicar cada etapa do CI/CD durante a execução | -20 |
| Não mostrar o artefato e os testes | -20 |
| Variáveis sensíveis desprotegidas | -15 |
| Não mostrar os recursos no portal | -10 |
| Não mostrar o app com a alteração | -30 |
| Faltar a evidência de cada operação do CRUD **no banco, por SELECT** | -30 |

O que muda em relação à Sprint 3:

- **O ambiente já começa pronto.** O enunciado pede "ferramentas visíveis e preparadas antes
  de gravar". Não há `git clone` nem criação de recursos no vídeo.
- **Cortes:** é permitido cortar a espera ociosa das pipelines, mas **cada etapa precisa
  aparecer e ser explicada** enquanto roda.

---

## Antes de gravar

Na ordem. Tudo isso fica **fora** do vídeo.

- [ ] **Infra ligada:** `cd scripts/sprint4 && ./98_retomar.sh` (~3 min, espera o Oracle
      sozinho)
- [ ] **CD reativado.** Ele fica desativado entre as sessões, para um merge na `main` com
      a infra desligada não deixar run vermelho no histórico:
      Azure DevOps → *Pipelines* → `petflow-cd` → **⋮** → *Settings* → **Enabled** → *Save*
- [ ] **CD rodado uma vez manualmente:** *Pipelines* → `petflow-cd` → **Run pipeline** → *Run*.
      Ele publica a versão atual da `main` no Web App. Assim as migrations novas (como a V15)
      rodam e são validadas **antes** do vídeo, e o deploy do vídeo só leva a alteração do
      `login.html`. Espere o run terminar **verde** (~6 min) e confira:

      ```bash
      curl -s https://rm561940-petflow-api-s4b.azurewebsites.net/actuator/health
      # {"status":"UP",...}
      ```

      Se o CD falhar aqui, **não grave**: o problema está na versão da `main`, e não no vídeo.
- [ ] **Carga inicial:** `./08_bootstrap.sh` (clínica, master, 2 tutores com pet)
- [ ] **Vídeo de Compliance gravado** (ele precisa da carga inicial)
- [ ] IDE com o projeto na branch **`develop`** atualizada (`git pull`) e **push testado**
- [ ] Abas abertas e autenticadas: GitHub (repositório), Azure DevOps (projeto
      *Sprint 4 - Azure DevOps*), Portal do Azure (grupo `rg-petflow-s4-rm561940`) e o app
      em `/login`
- [ ] **SQL Developer conectado** (tabela abaixo), com o
      [`EVIDENCIAS_VIDEO.sql`](EVIDENCIAS_VIDEO.sql) aberto
- [ ] Terminal Git Bash **na raiz do repositório**, na branch `develop`, com o preparo executado.
      O `TOKEN` fica para a pausa antes da Cena 10, porque expira em 15 minutos:

      ```bash
      export MSYS_NO_PATHCONV=1
      API="https://rm561940-petflow-api-s4b.azurewebsites.net/api"
      EMAIL="video.sprint4@exemplo.com"
      SENHA_TUTOR="video-$(openssl rand -hex 4)"
      clear
      ```
- [ ] Fonte grande no terminal e na IDE, notificações desligadas, microfone testado
- [ ] Este roteiro aberto numa segunda tela

### Conexão do SQL Developer

| Campo | Valor |
|---|---|
| Tipo de conexão | Básico |
| Hostname | `rm561940-s4b-db-petflow.brazilsouth.azurecontainer.io` |
| Porta | `1521` |
| **Nome do serviço** | `PETFLOWDB` (serviço, **não** SID) |
| Usuário | `petflow` |
| Senha | do Key Vault (comando abaixo) |

```bash
az keyvault secret show --vault-name kv-petflow-s4b-rm561940 --name oracle-app-password --query value -o tsv
```

### ⚠️ Três cuidados durante a gravação

**Nenhuma senha na tela.** Os comandos de senha (`az keyvault secret show`) rodam só no
preparo, antes de gravar. No log do CD, os segredos aparecem como `***`; é isso que você
mostra.

**Não reinicie o ACI do banco.** Não há volume: reiniciar zera os dados.

**Limite de login:** no máximo 5 por minuto por IP. A coleção do Newman (Compliance) faz 2
logins por execução. Até a pausa do `TOKEN` já terão passado vários minutos, então não há
conflito; só não refaça o `TOKEN` várias vezes seguidas.

---

## Roteiro

### O que o enunciado pede e onde está

O "Fluxo do vídeo" do enunciado tem 9 itens. **Cada um precisa aparecer**; esta tabela diz em
qual cena, e cada cena abaixo começa citando o item que cumpre. O roteiro tem **só** o que os
itens pedem, mais uma introdução de 20 segundos.

| # | Item do enunciado (texto literal) | Cena |
|---|---|---|
| 1 | *"Mostre as ferramentas usadas (IDE, Azure DevOps, Banco de Dados, Portal do Azure etc.)"* | 1 |
| 2 | *"Apresente a configuração da pipeline (cada jobs/tasks)"* | 2 e 3 |
| 3 | *"Altere algo no código no projeto, algo fácil de visualizar na tela o que foi alterado (Exemplo: label, título etc)"* | 4 e 5 |
| 4 | *"Mostre o início automático da pipeline após o commit"* | 6 |
| 5 | *"Explique cada etapa do CI/CD durante a execução - Abra os detalhes do fluxo de execução da sua pipeline e comente no vídeo"* | 6 e 8 |
| 6 | *"Mostre o artefato criado e os resultados dos testes"* | 7 |
| 7 | *"Demonstre no Portal do Azure os recursos atualizados pelo deploy (Web App ou Azure Container Instance)"* | 9 |
| 8 | *"Acesse a aplicação no Web App ou ACI e execute o CRUD completo, mostrando no banco cada operação (inserir, consultar, atualizar, deletar)"* | 10 e 11 |
| 9 | *"O CRUD pode ser feito via interface da aplicação ou via chamadas REST [...], porém as evidências de persistência devem ser efetuadas através de SELECT dentro do Banco"* | 11 |

### Introdução (~20 s)

Com o Azure DevOps na tela, no projeto *Sprint 4 - Azure DevOps*, diga:

> "Este vídeo é da **Sprint 4 de DevOps Tools & Cloud Computing**: o CI/CD do **PetFlow**, a API
> Java da Clyvo Vet, no **Azure DevOps**. Toda alteração de código na branch principal passa
> sozinha por build, testes, geração do artefato e deploy num **Azure Web App**, com o banco
> **Oracle em container no Azure**. Vou mostrar as ferramentas, a configuração das pipelines,
> uma alteração no código disparando o CI/CD, os recursos no portal e o CRUD com evidência no
> banco."

### Cena 1 · Ferramentas (~1 min) 🔵 item 1

> **Enunciado:** *"Mostre as ferramentas usadas (IDE, Azure DevOps, Banco de Dados, Portal do
> Azure etc.)"*

Passe por cada janela, na ordem, com uma frase em cada:

| Janela | O que mostrar | O que falar |
|---|---|---|
| **IDE** | O projeto aberto, com as pastas `src/` e `pipelines/` | "O código da aplicação, em Java 21 com Spring Boot, e as pipelines em YAML" |
| **GitHub** | A página do repositório `olavoneves/clyvo-vet_api-java` | "O repositório, ligado ao Azure DevOps" |
| **Azure DevOps** | *Pipelines*, com o `petflow-ci` e o `petflow-cd` | "As duas pipelines: integração contínua e entrega contínua" |
| **Portal do Azure** | O grupo `rg-petflow-s4-rm561940` com a lista de recursos | "Os recursos na nuvem: o Web App e o banco no Container Instance" |
| **SQL Developer** | A conexão aberta no banco | "O banco Oracle em container no Azure" |
| **Navegador** | O app em `/login` | "A aplicação publicada" |

### Cena 2 · Pipeline de CI, task por task (~2 min) 🔵 item 2

> **Enunciado:** *"Apresente a configuração da pipeline (cada jobs/tasks)"*. Vale para as
> **duas** pipelines: o CI aqui, o CD na cena seguinte.

No Azure DevOps: **Pipelines → `petflow-ci` → Edit** (canto superior direito). Abre o
`pipelines/ci.yml` com os números de linha. Percorra de cima para baixo:

| Linhas | O que apontar | O que falar |
|---|---|---|
| 21–24 | `trigger: branches: include: main` | "O CI dispara a cada alteração na branch `main`, que faz o papel da `master` do enunciado, com uso autorizado pelo professor" |
| 27 | `pr: none` | "Pull requests abertos não disparam; o que dispara é o código entrar na `main`" |
| 29–30 | `pool: vmImage: ubuntu-latest` | "Roda num agente Linux hospedado pela Microsoft" |
| 44–45 | `Cache@2` | "Guarda as dependências do Maven entre execuções, para o build ser mais rápido" |
| 60–70 | `Maven@4` com `goals: clean verify` | "Compila e **executa os testes automáticos**. A linha 68, `publishJUnitResults`, publica o resultado dos testes no Azure DevOps. Se um teste falha, não há artefato e o CD não dispara" |
| 74–75 | `CopyFiles@2` | "Separa só o JAR executável da aplicação" |
| 81–85 | `PublishPipelineArtifact@1`, `artifact: petflow-jar` | "**Publica o artefato** `petflow-jar`. É ele que dispara o CD" |

Clique em **Cancel** ou volte, sem salvar nada.

### Cena 3 · Pipeline de CD, task por task (~2 min) 🔵 item 2

> **Enunciado:** *"Apresente a configuração da pipeline (cada jobs/tasks)"*.

**Pipelines → `petflow-cd` → Edit** (`pipelines/cd.yml`):

| Linhas | O que apontar | O que falar |
|---|---|---|
| 22–23 | `trigger: none`, `pr: none` | "O CD não dispara por push" |
| 25–32 | `resources: pipelines: source: petflow-ci`, `branches: main` | "Ele dispara **quando o CI termina com sucesso na `main`**, ou seja, a cada novo artefato" |
| 35 | `group: petflow-secrets` | "Os segredos vêm de um variable group" |
| 49 | `environment: petflow-producao` | "O deploy fica registrado num ambiente, com o histórico de versões publicadas" |
| 54–56 | `download: ci`, `artifact: petflow-jar` | "Baixa **o mesmo JAR** que o CI gerou" |
| 58–71 | `AzureWebApp@1` e o `appSettings` | "Publica o JAR no Web App e grava as configurações: a URL do banco, o usuário, a senha, o segredo do JWT e a senha do usuário master, todas vindas do variable group" |
| 73–90 | o script do health check | "No fim, confere o `/actuator/health`; ele só responde `UP` se a aplicação subiu e alcança o banco" |

Clique em **Cancel**. Depois: **Pipelines → Library → `petflow-secrets`**. Aponte os
**cadeados** em `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` e `MASTER_PASSWORD`:

> "Usuários, senhas e tokens estão marcados como secretos: não aparecem aqui e saem como `***`
> nos logs."

### Cena 4 · O app antes da alteração (~30 s) 🔵 item 3

> **Enunciado:** *"Altere algo no código no projeto, algo fácil de visualizar na tela o que foi
> alterado"*. Mostrar o **antes** é o que torna a alteração visível na Cena 10.

No navegador, abra `https://rm561940-petflow-api-s4b.azurewebsites.net/login`. Aponte o
subtítulo embaixo do logo: **"Entre com seu e-mail e senha · Sprint 4 CI/CD"**.

> "Esta é a tela de login publicada agora. Vou alterar este subtítulo no código."

### Cena 5 · Alteração no código (~2 min) 🔵 item 3

> **Enunciado:** *"Altere algo no código no projeto, algo fácil de visualizar na tela o que foi
> alterado (Exemplo: label, título etc)"*. É **código**, nunca o README (-20).

**Passo 1. A alteração.** Na IDE, abra `src/main/resources/templates/login.html` e vá até a
**linha 20**:

```html
<p class="login-sub">Entre com seu e-mail e senha · Sprint 4 CI/CD</p>
```

Troque por:

```html
<p class="login-sub">Entre com seu e-mail e senha · Sprint 4 · CI/CD no Azure DevOps</p>
```

Salve (**Ctrl+S**).

**Passo 2. Commit e push na `develop`.** No terminal, na raiz do repositório:

```bash
git status                       # mostra o login.html modificado
git add src/main/resources/templates/login.html
git commit -m "feat: destaca o CI/CD no Azure DevOps na tela de login"
git push
```

> "O repositório segue Git Flow: a `main` não recebe commit direto. A alteração vai para a
> `develop` e chega à `main` por pull request."

**Passo 3. Pull request e merge.** No GitHub, na página do repositório:
1. Clique em **Compare & pull request** (ou *Pull requests → New pull request*)
2. Base: **`main`** ← compare: **`develop`**
3. Preencha o **título** e a **descrição** abaixo (deixe copiados antes de gravar)
4. Na aba **Files changed**, aponte o `login.html`. Se aparecerem outros arquivos em `docs/`,
   diga que são documentação (os roteiros)
5. **Create pull request** → **Merge pull request** → **Confirm merge**

**Título:**

```
feat: destaca o CI/CD no Azure DevOps na tela de login
```

**Descrição:**

```markdown
## Alteração

Subtítulo da tela de login (`src/main/resources/templates/login.html`):

- **Antes:** "Entre com seu e-mail e senha · Sprint 4 CI/CD"
- **Depois:** "Entre com seu e-mail e senha · Sprint 4 · CI/CD no Azure DevOps"

## O que acontece no merge

O merge na `main` dispara automaticamente:

1. **CI (`petflow-ci`):** build com Maven, testes automáticos e publicação do artefato `petflow-jar`
2. **CD (`petflow-cd`):** deploy do artefato no Azure Web App e verificação de saúde da aplicação

Também leva para a `main` os roteiros dos vídeos da Sprint 4 (DevOps e Compliance), já revisados na `develop`.
```

> "O merge é a alteração de código na `main`. A partir daqui, nada mais é manual."

### Cena 6 · O CI dispara sozinho (~3 min) 🔵 itens 4 e 5

> **Enunciado:** *"Mostre o início automático da pipeline após o commit"* e *"Explique cada etapa
> do CI/CD durante a execução - Abra os detalhes do fluxo de execução da sua pipeline e comente
> no vídeo"*.

**Passo 1. O disparo automático.** Vá ao Azure DevOps → **Pipelines** (recarregue a página se
preciso). Em alguns segundos aparece um run novo do `petflow-ci`. Clique nele e aponte no
topo:
- **"Individual CI"**: o disparo foi automático, por alteração no repositório
- o **commit**: é o commit de merge do PR, na `main`

> "Ninguém clicou em executar: o merge na `main` disparou o CI."

**Passo 2. Cada etapa.** Clique no job **"Build, testes e publicacao do artefato"**. À
esquerda aparece a lista de etapas; clique em cada uma **enquanto roda** e comente:

| Etapa | O que falar |
|---|---|
| *Checkout olavoneves/clyvo-vet_api-java@main* | "Baixa o código da `main` do GitHub" |
| *Restaura o cache do Maven* | "Recupera as dependências guardadas da execução anterior" |
| *Build e testes automaticos (mvn clean verify)* | "Compila e roda os testes". No log, role até as linhas **`Tests run:`** e aponte uma |
| *Separa o JAR executavel* | "Separa o JAR gerado" |
| *Publica o artefato petflow-jar* | "Publica o artefato no Azure DevOps" |

O CI leva uns 2 minutos. **Pode cortar a espera**, mas cada etapa precisa aparecer comentada.

### Cena 7 · Artefato e resultado dos testes (~1 min) 🔵 item 6

> **Enunciado:** *"Mostre o artefato criado e os resultados dos testes"*.

Volte ao **resumo do run** do CI (a seta de voltar no topo):

**Passo 1. Os testes.** Clique na aba **Tests**. Aponte: **130 aprovados** e **40 pulados**
(confira os números do dia).

> "Os 40 pulados são testes de integração que precisam do banco Oracle e não rodam no agente
> do CI. Os de unidade, segurança e telas rodam todos, e nenhum falhou."

**Passo 2. O artefato.** Volte à aba **Summary**. Em *Related*, clique em **1 published** →
expanda **`petflow-jar`** → aponte o arquivo **`.jar`**.

> "Este é o artefato gerado pelo CI: o JAR que o CD vai publicar."

### Cena 8 · O CD dispara sozinho (~3 min) 🔵 item 5

> **Enunciado:** *"Explique cada etapa do CI/CD durante a execução - Abra os detalhes do fluxo de
> execução da sua pipeline e comente no vídeo"*.

**Passo 1. O disparo.** Em **Pipelines**, o `petflow-cd` aparece rodando logo depois do CI.
Clique nele e aponte no resumo que foi disparado **pelo `petflow-ci`**, e não manualmente.

> "O CD começou sozinho quando o CI publicou o artefato novo."

**Passo 2. Cada etapa.** Clique no job **"Deploy do JAR no Web App"** e em cada etapa:

| Etapa | O que falar |
|---|---|
| *Baixa o artefato gerado pelo CI* | "Baixa o mesmo JAR que acabamos de ver no CI" |
| *Publica o JAR e aplica as app settings* | "Publica no Web App". No log, procure o `appSettings` e aponte os valores como **`***`**: "as senhas não aparecem" |
| *Verifica a saude da aplicacao (smoke test)* | "Espera a aplicação subir". Aponte as tentativas e a última linha, **"Aplicacao no ar ... UP"** |

O CD leva uns 6 minutos, a maior parte esperando a aplicação subir. **Corte a espera**, mas
mostre a linha final do `UP` e o run **verde**.

### Cena 9 · Recursos no Portal (~1 min) 🔵 item 7

> **Enunciado:** *"Demonstre no Portal do Azure os recursos atualizados pelo deploy (Web App ou
> Azure Container Instance)"*. O recurso que o deploy atualizou é o **Web App**.

No Portal do Azure, no grupo `rg-petflow-s4-rm561940`:

**Passo 1. O Web App.** Clique em **`rm561940-petflow-api-s4b`**:
- **Overview**: *Status* **Running** e o *Default domain*
- Menu lateral **Deployment Center** → aba **Logs**: o deploy que acabou de acontecer, com a
  data e a hora de agora

> "Este é o deploy que o CD acabou de fazer."

**Passo 2. O banco.** Volte ao grupo e clique em **`rm561940-aci-db`**: *Status* **Running**,
porta **1521**.

> "E aqui o banco Oracle em container, onde a aplicação grava os dados."

### ⏸ Pausa: token e senha do master (fora do vídeo)

**Pause a gravação.** O token de acesso da API expira em 15 minutos, e a esta altura o vídeo já
tem mais do que isso. No terminal:

```bash
TOKEN=$(curl -s -X POST $API/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"master@clyvovet.com\",\"senha\":\"$(az keyvault secret show \
       --vault-name kv-petflow-s4b-rm561940 --name master-password \
       --query value -o tsv | tr -d '\r')\",\"tipo\":\"COLABORADOR\"}" \
  | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
echo ${#TOKEN}        # deve imprimir ~250, não 0

# Copia a senha do master para a área de transferência, para o login na tela (Cena 11, Passo 4)
az keyvault secret show --vault-name kv-petflow-s4b-rm561940 --name master-password \
  --query value -o tsv | tr -d '\r\n' | clip

clear
```

**Retome a gravação.**

### Cena 10 · O app com a alteração (~30 s) 🔵 itens 3 e 8

> **Enunciado:** *"Acesse a aplicação no Web App"*. Aqui aparece a alteração da Cena 5 já
> publicada pelo CD (sem isso, -30).

No navegador, recarregue `/login` (**Ctrl+F5**). O subtítulo agora é
**"Entre com seu e-mail e senha · Sprint 4 · CI/CD no Azure DevOps"**.

> "A alteração está no ar, publicada pelo CD, sem nenhum passo manual."

### Cena 11 · CRUD de tutor e pet com SELECT (~7 min) 🔵 itens 8 e 9

> **Enunciado:** *"execute o CRUD completo, mostrando no banco cada operação (inserir, consultar,
> atualizar, deletar)"* e *"O CRUD pode ser feito via interface da aplicação ou via chamadas
> REST [...], porém as evidências de persistência devem ser efetuadas através de SELECT dentro
> do Banco"*.
>
> As telas do PetFlow só exibem dados: cadastro, edição e exclusão existem só na API. Por isso
> o CRUD é por **chamadas REST** (`curl`), nas **duas tabelas relacionadas** (tutor 1:N pet),
> e **cada operação** é seguida do seu `SELECT`. A resposta da API não conta como evidência; o
> `SELECT`, sim. A tela `/pets` mostra o efeito na aplicação.

Deixe **duas janelas lado a lado**: o terminal à esquerda e o SQL Developer à direita, com o
[`EVIDENCIAS_VIDEO.sql`](EVIDENCIAS_VIDEO.sql) aberto. O arquivo está numerado na mesma ordem
desta cena: para rodar uma consulta, clique nela e aperte **Ctrl+Enter**.

**Passo 1. O banco é o da nuvem.** Rode a consulta da seção **0** (linha 33):

> "O servidor aparece como `SandboxHost`: é o container no Azure, não um banco local."

**Passo 2. INSERIR o tutor.**

```bash
curl -i -X POST $API/tutores \
  -H "Content-Type: application/json" \
  -d "{\"nome\":\"Tutor Video Sprint 4\",\"email\":\"$EMAIL\",\"telefone\":\"11911112222\",\"senha\":\"$SENHA_TUTOR\",\"clinicaId\":1}"
```

Aponte o **`201 Created`** e o `"id"` na resposta. Guarde: `ID_TUTOR=<o número>`.
SQL Developer, seção **1** (linha 42): a linha do tutor no banco.

**Passo 3. INSERIR o pet desse tutor.**

```bash
curl -i -X POST $API/pets \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"nome\":\"Thor\",\"dtNascimento\":\"2021-06-10\",\"sexo\":\"M\",\"porte\":\"GRANDE\",\"castrado\":true,\"status\":\"ATIVO\",\"tutorId\":$ID_TUTOR,\"racaId\":1}"
```

Aponte o **`201 Created`** e o `"id"`. Guarde: `ID_PET=<o número>`.
SQL Developer, seção **2** (linha 51): o pet ligado ao tutor.

> "O pet aponta para o tutor pela chave estrangeira: são duas tabelas relacionadas."

**Passo 4. Na aplicação.** Na aba do navegador, ainda em `/login`, entre como colaborador:
- E-mail: `master@clyvovet.com`
- Senha: **Ctrl+V** (a senha copiada na pausa; o campo a mascara)

Clique em **Entrar**. No menu, abra **Pets** (`/pets`): o **Thor** aparece, com o tutor
**Tutor Video Sprint 4**, ao lado dos pets da carga inicial (Luna e Mingau).

> "O pet que acabei de cadastrar pela API já aparece na aplicação."

Deixe esta aba aberta: nos Passos 6 e 9 basta recarregar.

**Passo 5. CONSULTAR.**

```bash
curl -s -H "Authorization: Bearer $TOKEN" $API/tutores/$ID_TUTOR
curl -s -H "Authorization: Bearer $TOKEN" $API/pets/$ID_PET
```

SQL Developer, seção **3** (linha 61): os mesmos dados no banco.

**Passo 6. ATUALIZAR o tutor e o pet.**

```bash
curl -i -X PUT $API/tutores/$ID_TUTOR \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"nome\":\"Tutor Video ALTERADO\",\"email\":\"$EMAIL\",\"telefone\":\"11933334444\",\"senha\":\"$SENHA_TUTOR\",\"clinicaId\":1}"

curl -i -X PUT $API/pets/$ID_PET \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"nome\":\"Thor do Vale\",\"dtNascimento\":\"2021-06-10\",\"sexo\":\"M\",\"porte\":\"GIGANTE\",\"castrado\":true,\"status\":\"ATIVO\",\"tutorId\":$ID_TUTOR,\"racaId\":1}"
```

Aponte os dois **`200 OK`**. SQL Developer, seção **4** (linha 72): nome e telefone do tutor,
nome e porte do pet, todos novos. Na aba `/pets`, recarregue: agora é **Thor do Vale**.

**Passo 7. A integridade: excluir o tutor que tem pet.**

```bash
curl -i -X DELETE $API/tutores/$ID_TUTOR -H "Authorization: Bearer $TOKEN"
```

Aponte o **`409 Conflict`**, e **não** um 500.

> "O banco não deixa apagar um tutor que tem pet: a chave estrangeira impede registros órfãos,
> e a aplicação devolve um erro legível."

SQL Developer, seção **5** (linhas 83 e 89): o tutor e o pet continuam lá, e a constraint
`FK_PET_TUTOR` que garante isso.

**Passo 8. DELETAR, na ordem certa: primeiro o pet.**

```bash
curl -i -X DELETE $API/pets/$ID_PET -H "Authorization: Bearer $TOKEN"
```

**`204 No Content`**. SQL Developer, seção **6** (linha 98): volta **vazia**.

**Passo 9. Depois o tutor.**

```bash
curl -i -X DELETE $API/tutores/$ID_TUTOR -H "Authorization: Bearer $TOKEN"
```

**`204 No Content`**: agora funciona, porque o tutor não tem mais pet. SQL Developer, seção
**7** (linha 108): volta **vazia**. Na aba `/pets`, recarregue: o Thor sumiu.

> "As quatro operações foram feitas pela API, nas duas tabelas, e cada uma foi comprovada por
> SELECT no banco."

> **Erros esperados, que não são bugs:**
>
> **422** num `PUT`: ele reusa o DTO do `POST`, então os campos obrigatórios precisam ir no
> corpo. Os comandos acima já os incluem.
>
> **401** com `"Token de acesso ausente ou invalido"`: o token expirou. Pause, refaça o `TOKEN`
> e siga.
>
> **409 no POST do tutor**: sobrou um tutor com o mesmo e-mail de uma tentativa anterior.
> Apague-o (`DELETE /tutores/<id>`, depois de apagar o pet dele) e repita.

---

## Depois de gravar

```bash
cd scripts/sprint4 && ./97_pausar.sh
```

E **desative o `petflow-cd`** de novo (*Pipelines* → `petflow-cd` → **⋮** → *Settings* →
**Disabled**).

Com o link do YouTube:
- Gere o PDF da entrega com o link do vídeo
- Coloque o link no README, na seção de DevOps

---

## Duração estimada

| Bloco | Tempo |
|---|---|
| Introdução | ~20 s |
| Cena 1 (ferramentas) | ~1 min |
| Cenas 2–3 (configuração das pipelines) | ~4 min |
| Cenas 4–5 (alteração no código) | ~2,5 min |
| Cenas 6–8 (CI e CD rodando) | ~7 min com a espera cortada |
| Cena 9 (portal) | ~1 min |
| Cena 10 (app com a alteração) | ~30 s |
| Cena 11 (CRUD de tutor e pet com SELECT) | ~7 min |

Total: **cerca de 23 minutos**. As pausas das pipelines são o que mais pesa: corte a espera
ociosa, mantendo cada etapa na tela enquanto é explicada.

---

## Conferência final antes de publicar

- [ ] 720p ou superior
- [ ] Áudio audível do início ao fim
- [ ] **Nenhuma legenda** ativada
- [ ] Nenhuma senha visível em nenhum frame
- [ ] **Item 1:** IDE, Azure DevOps, banco e Portal do Azure aparecem
- [ ] **Item 2:** cada task do CI e do CD foi apresentada
- [ ] **Item 3:** a alteração é no **código** (`login.html`), não no README, com o antes e o depois na tela
- [ ] **Item 4:** o CI aparece disparando **sozinho** depois do merge na `main`, e o CD sozinho depois do CI
- [ ] **Item 5:** cada etapa do CI e do CD foi explicada **enquanto rodava**, com os detalhes do run abertos
- [ ] **Item 6:** a aba Tests e o artefato `petflow-jar` aparecem
- [ ] **Item 7:** o Web App aparece no Portal com o deploy que acabou de acontecer
- [ ] **Item 8:** o app aparece com a alteração, e as 4 operações do CRUD (inserir, consultar, atualizar, deletar) foram feitas no tutor e no pet, com o 409 da integridade
- [ ] **Item 9:** cada operação tem o seu `SELECT` no banco
- [ ] Publicado como **não listado** e o link testado numa janela anônima
