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
- [ ] Terminal Git Bash com o **preparo** do `EVIDENCIAS_VIDEO.sql` já executado (`API`,
      `EMAIL`, `SENHA_TUTOR`, `TOKEN`) e a tela limpa (`clear`)
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

**Limite de login:** no máximo 5 por minuto por IP. A coleção do Postman (Compliance) já
fez 2. Se o `TOKEN` precisar ser refeito, espere 1 minuto depois do vídeo de Compliance.

---

## Roteiro

### O que o enunciado pede e onde está

O "Fluxo do vídeo" do enunciado tem 9 itens. **Cada um precisa aparecer**; esta tabela diz em
qual cena, e cada cena abaixo começa citando o item que cumpre. O roteiro tem **só** o que os
itens pedem: nada de abertura, apresentação do projeto ou encerramento.

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

### Cena 1 · Ferramentas (~1 min) 🔵 item 1

> **Enunciado:** *"Mostre as ferramentas usadas (IDE, Azure DevOps, Banco de Dados, Portal do
> Azure etc.)"*

Passe por cada janela, uma frase em cada: IDE com o código, GitHub, Azure DevOps, Portal do
Azure, SQL Developer e o app no navegador.

### Cena 2 · Pipeline de CI, task por task (~2 min) 🔵 item 2

> **Enunciado:** *"Apresente a configuração da pipeline (cada jobs/tasks)"*. Vale para as
> **duas** pipelines: o CI aqui, o CD na cena seguinte.

Abra o `petflow-ci` → *Edit* (ou `pipelines/ci.yml` na IDE) e explique:

| Trecho | O que dizer |
|---|---|
| `trigger: branches: main` | Dispara a cada alteração na `main`, que faz o papel da `master` (uso autorizado pelo professor) |
| `pool: vmImage: ubuntu-latest` | Agente hospedado pela Microsoft |
| `Cache@2` | Guarda as dependências do Maven entre execuções |
| `Maven@4` com `clean verify` | Compila e **executa os testes automáticos**. `publishJUnitResults` publica o resultado. Se um teste falha, não há artefato e o CD não dispara |
| `CopyFiles@2` | Separa só o JAR executável |
| `PublishPipelineArtifact@1` | **Publica o artefato** `petflow-jar`, que é o gatilho do CD |

### Cena 3 · Pipeline de CD, task por task (~2 min) 🔵 item 2

> **Enunciado:** *"Apresente a configuração da pipeline (cada jobs/tasks)"*.

Abra o `petflow-cd` → *Edit* (ou `pipelines/cd.yml`) e explique:

| Trecho | O que dizer |
|---|---|
| `trigger: none` + `resources.pipelines` | Não dispara por push: dispara **quando o CI gera um novo artefato** |
| `variables: group: petflow-secrets` | Os segredos vêm do variable group. Abra *Library → petflow-secrets* e mostre os **cadeados** em `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` e `MASTER_PASSWORD` |
| `deployment` + `environment: petflow-producao` | Guarda o histórico de deploys |
| `download: ci` | Baixa o mesmo JAR que o CI gerou |
| `AzureWebApp@1` | Publica o JAR no Web App e grava as app settings (banco, usuário, senha, JWT) |
| Health check | Espera o `/actuator/health` responder `UP`, o que só acontece com o banco alcançável |

### Cena 4 · O app antes da alteração (~30 s) 🔵 item 3

> **Enunciado:** *"Altere algo no código no projeto, algo fácil de visualizar na tela o que foi
> alterado"*. Mostrar o **antes** é o que torna a alteração visível na Cena 10.

No navegador, abra `https://rm561940-petflow-api-s4b.azurewebsites.net/login` e mostre o
subtítulo **"Entre com seu e-mail e senha"**.

### Cena 5 · Alteração no código (~2 min) 🔵 item 3

> **Enunciado:** *"Altere algo no código no projeto, algo fácil de visualizar na tela o que foi
> alterado (Exemplo: label, título etc)"*. É **código**, nunca o README (-20).

Na IDE, em `src/main/resources/templates/login.html`:

```html
<p class="login-sub">Entre com seu e-mail e senha</p>
```

vira:

```html
<p class="login-sub">Entre com seu e-mail e senha · Sprint 4 CI/CD</p>
```

Seguindo o Git Flow do repositório (a `main` não recebe commit direto):

```bash
git add src/main/resources/templates/login.html
git commit -m "feat: destaca a Sprint 4 na tela de login"
git push
```

No GitHub, abra o PR **`develop → main`** e faça o **merge**. Diga em voz: "o merge é a
alteração de código na `main` que deve disparar o CI sozinho".

### Cena 6 · O CI dispara sozinho (~3 min) 🔵 itens 4 e 5

> **Enunciado:** *"Mostre o início automático da pipeline após o commit"* e *"Explique cada etapa
> do CI/CD durante a execução - Abra os detalhes do fluxo de execução da sua pipeline e comente
> no vídeo"*.
>
> O "commit" que chega à `main` é o **commit de merge** do PR: é ele que aparece no run como
> origem do disparo. Diga isso em voz.

Azure DevOps → *Pipelines*: o run do `petflow-ci` aparece sozinho. Mostre o gatilho
(*Individual CI*) e o commit do merge.

Abra o run e clique em cada etapa enquanto ela roda:

- *Checkout*: baixa o código do GitHub
- *Restaura o cache do Maven*
- *Build e testes automáticos*: no log, aponte as linhas `Tests run:`
- *Separa o JAR executável*
- *Publica o artefato petflow-jar*

O CI leva ~2 min.

### Cena 7 · Artefato e resultado dos testes (~1 min) 🔵 item 6

> **Enunciado:** *"Mostre o artefato criado e os resultados dos testes"*.

No resumo do run do CI:
- Aba **Tests**: **130 aprovados e 40 pulados** (números do CI de 27/10; confira os do dia). Explique os pulados: "são testes de
  integração que precisam do Oracle e não rodam no agente do CI".
- **Artefato publicado**: *1 published* → `petflow-jar` → o `.jar`.

### Cena 8 · O CD dispara sozinho (~3 min) 🔵 item 5

> **Enunciado:** *"Explique cada etapa do CI/CD durante a execução - Abra os detalhes do fluxo de
> execução da sua pipeline e comente no vídeo"*. Abra o run do CD e clique em cada etapa
> enquanto ela roda.

Em *Pipelines*, o `petflow-cd` aparece sozinho logo depois do CI. O gatilho é o run do CI
(*Triggered by petflow-ci*).

- *Baixa o artefato gerado pelo CI*
- *Publica o JAR e aplica as app settings*: mostre no log os segredos como `***`
- *Verifica a saúde da aplicação*: as tentativas até o `UP`. A primeira subida leva uns
  3 minutos; **corte a espera**, mas mostre o resultado.

O CD leva ~6 min.

### Cena 9 · Recursos no Portal (~1 min) 🔵 item 7

> **Enunciado:** *"Demonstre no Portal do Azure os recursos atualizados pelo deploy (Web App ou
> Azure Container Instance)"*. O recurso que o deploy atualizou é o **Web App**.

No grupo `rg-petflow-s4-rm561940`:
- **Web App** `rm561940-petflow-api-s4b`: *Overview* em `Running` e *Deployment Center* ou
  *Activity log* com o deploy que acabou de acontecer
- **ACI** `rm561940-aci-db`: o Oracle em `Running`, onde a aplicação grava

### Cena 10 · O app com a alteração (~30 s) 🔵 itens 3 e 8

> **Enunciado:** *"Acesse a aplicação no Web App"*. Aqui aparece a alteração da Cena 5 já
> publicada pelo CD (sem isso, -30).

Recarregue `/login`: o subtítulo agora é **"Entre com seu e-mail e senha · Sprint 4 CI/CD"**.

### Cena 11 · CRUD do tutor com SELECT (~4 min) 🔵 itens 8 e 9

> **Enunciado:** *"execute o CRUD completo, mostrando no banco cada operação (inserir, consultar,
> atualizar, deletar)"* e *"O CRUD pode ser feito via interface da aplicação ou via chamadas
> REST [...], porém as evidências de persistência devem ser efetuadas através de SELECT dentro
> do Banco"*.
>
> Aqui o CRUD é por **chamadas REST** (`curl`), e **cada uma das 4 operações** é seguida do seu
> `SELECT` no SQL Developer. A resposta da API não conta como evidência; o `SELECT`, sim.

Duas janelas lado a lado: terminal e SQL Developer. Os comandos estão no
[`EVIDENCIAS_VIDEO.sql`](EVIDENCIAS_VIDEO.sql), na ordem; este roteiro só narra.

Comece rodando **só a primeira consulta** do arquivo: o host aparece como `SandboxHost-...`,
o que prova que o banco é o container na Azure e não um banco local. Depois:

| Operação | No terminal | No SQL Developer |
|---|---|---|
| **Inserir** | `POST /tutores` → **201**. Anote o `id` (`ID=...`) | A linha do `video.sprint4@exemplo.com` aparece |
| **Consultar** | `GET /tutores/$ID` → **200** | Os mesmos dados |
| **Atualizar** | `PUT /tutores/$ID` → **200** | Nome e telefone novos |
| **Deletar** | `DELETE /tutores/$ID` → **204** | A consulta volta **vazia** |

> **Dois erros esperados, que não são bugs:**
>
> **422** no `PUT`: ele reusa o DTO do `POST`, então `senha` e `clinicaId` são obrigatórios
> no corpo.
>
> **401** com `"Token de acesso ausente ou invalido"`: o token expira em 15 minutos. Refaça
> o `TOKEN` (fora do enquadramento, porque o comando busca a senha no Key Vault) e siga.

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
| Cena 1 (ferramentas) | ~1 min |
| Cenas 2–3 (configuração das pipelines) | ~4 min |
| Cenas 4–5 (alteração no código) | ~2,5 min |
| Cenas 6–8 (CI e CD rodando) | ~7 min com a espera cortada |
| Cenas 9–10 (portal e app) | ~1,5 min |
| Cena 11 (CRUD com SELECT) | ~4 min |

Total: **cerca de 20 minutos**. As pausas das pipelines são o que mais pesa: corte a espera
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
- [ ] **Item 8:** o app aparece com a alteração, e as 4 operações do CRUD (inserir, consultar, atualizar, deletar) foram feitas
- [ ] **Item 9:** cada uma das 4 operações tem o seu `SELECT` no banco
- [ ] Publicado como **não listado** e o link testado numa janela anônima
