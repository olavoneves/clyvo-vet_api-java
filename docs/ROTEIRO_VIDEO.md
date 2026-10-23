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
- [ ] **App no ar.** O banco voltou zerado, e o Flyway recria o schema na subida (~4 min
      no B1):

      ```bash
      curl -s https://rm561940-petflow-api-s4b.azurewebsites.net/actuator/health
      # {"status":"UP",...}
      ```
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

A ordem segue o "Fluxo do vídeo" do enunciado.

### Cena 1 · Abertura e arquitetura (~2 min)

Diga em voz:
- O que a aplicação faz, em duas frases: o PetFlow acompanha a saúde do pet de forma
  contínua; o motor de protocolo em PL/SQL transforma cada consulta nas obrigações futuras
  de cuidado.
- A stack: Java 21 e Spring Boot, Oracle XE em container no Azure, Azure DevOps para CI/CD.

Mostre o diagrama do README (`docs/arquitetura/sprint4-cicd.png`) e percorra os números de
1 a 11:

1. O desenvolvedor envia a alteração para a `main`
2. O webhook do GitHub dispara o CI
3. Build com Maven e testes automáticos
4. Publicação do artefato
5. O novo artefato dispara o CD
6. O CD recebe os segredos protegidos do variable group
7. Deploy do JAR no Web App, pela service connection
8. A aplicação conecta no Oracle e o Flyway aplica as migrations
9. O CD confere a saúde da aplicação
10. O usuário final usa a aplicação
11. Os dados são persistidos no Oracle

As personas são o **desenvolvedor** e o **usuário final** (clínica veterinária).

### Cena 2 · Ferramentas (~1 min)

Passe por cada janela: IDE com o código, GitHub, Azure DevOps, Portal do Azure, SQL
Developer e o app no navegador.

### Cena 3 · Projeto no Azure DevOps (~1 min)

- *Project settings → Overview*: nome **Sprint 4 - Azure DevOps**, descrição com o
  professor e os integrantes, **Private**, **Git**, **Scrum**.
- *Organization settings → Users*: o professor com acesso **Basic**.

### Cena 4 · Pipeline de CI, task por task (~2 min)

Abra o `petflow-ci` → *Edit* (ou `pipelines/ci.yml` na IDE) e explique:

| Trecho | O que dizer |
|---|---|
| `trigger: branches: main` | Dispara a cada alteração na `main`, que faz o papel da `master` (uso autorizado pelo professor) |
| `pool: vmImage: ubuntu-latest` | Agente hospedado pela Microsoft |
| `Cache@2` | Guarda as dependências do Maven entre execuções |
| `Maven@4` com `clean verify` | Compila e **executa os testes automáticos**. `publishJUnitResults` publica o resultado. Se um teste falha, não há artefato e o CD não dispara |
| `CopyFiles@2` | Separa só o JAR executável |
| `PublishPipelineArtifact@1` | **Publica o artefato** `petflow-jar`, que é o gatilho do CD |

### Cena 5 · Pipeline de CD, task por task (~2 min)

Abra o `petflow-cd` → *Edit* (ou `pipelines/cd.yml`) e explique:

| Trecho | O que dizer |
|---|---|
| `trigger: none` + `resources.pipelines` | Não dispara por push: dispara **quando o CI gera um novo artefato** |
| `variables: group: petflow-secrets` | Os segredos vêm do variable group |
| `deployment` + `environment: petflow-producao` | Guarda o histórico de deploys |
| `download: ci` | Baixa o mesmo JAR que o CI gerou |
| `AzureWebApp@1` | Publica o JAR no Web App e grava as app settings (banco, usuário, senha, JWT) |
| Health check | Espera o `/actuator/health` responder `UP`, o que só acontece com o banco alcançável |

Em seguida:
- *Pipelines → Library → petflow-secrets*: `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` e
  `MASTER_PASSWORD` com **cadeado**. "Usuários, senhas e tokens ficam protegidos e
  mascarados nos logs."
- *Project settings → Service connections*: `sc-github-petflow` (OAuth com o GitHub) e
  `sc-azure-petflow` (federação de identidade com o Azure, sem senha guardada).

### Cena 6 · O app antes da alteração (~30 s)

No navegador, abra `https://rm561940-petflow-api-s4b.azurewebsites.net/login` e mostre o
subtítulo **"Entre com seu e-mail e senha"**.

### Cena 7 · Alteração no código (~2 min) 🔵 código, nunca o README

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

### Cena 8 · O CI dispara sozinho (~3 min) 🔵 explicar cada etapa

Azure DevOps → *Pipelines*: o run do `petflow-ci` aparece sozinho. Mostre o gatilho
(*Individual CI*) e o commit do merge.

Abra o run e clique em cada etapa enquanto ela roda:

- *Checkout*: baixa o código do GitHub
- *Restaura o cache do Maven*
- *Build e testes automáticos*: no log, aponte as linhas `Tests run:`
- *Separa o JAR executável*
- *Publica o artefato petflow-jar*

O CI leva ~2 min.

### Cena 9 · Artefato e resultado dos testes (~1 min)

No resumo do run do CI:
- Aba **Tests**: **128 aprovados e 40 pulados**. Explique os pulados: "são testes de
  integração que precisam do Oracle e não rodam no agente do CI; os de unidade, segurança e
  renderização das telas rodam todos".
- **Artefato publicado**: *1 published* → `petflow-jar` → o `.jar`.

### Cena 10 · O CD dispara sozinho (~3 min) 🔵 explicar cada etapa

Em *Pipelines*, o `petflow-cd` aparece sozinho logo depois do CI. O gatilho é o run do CI
(*Triggered by petflow-ci*).

- *Baixa o artefato gerado pelo CI*
- *Publica o JAR e aplica as app settings*: mostre no log os segredos como `***`
- *Verifica a saúde da aplicação*: as tentativas até o `UP`. A primeira subida leva uns
  3 minutos; pode cortar a espera, mas mostre o resultado.

O CD leva ~6 min.

### Cena 11 · Recursos no Portal (~1 min)

No grupo `rg-petflow-s4-rm561940`:
- **Web App** `rm561940-petflow-api-s4b`: *Overview* em `Running` e *Deployment Center* ou
  *Activity log* com o deploy que acabou de acontecer
- **ACI** `rm561940-aci-db`: o Oracle em `Running`, porta 1521
- **ACR**, **Key Vault** e **Managed Identity**: uma frase sobre cada um

### Cena 12 · O app com a alteração (~30 s)

Recarregue `/login`: o subtítulo agora é **"Entre com seu e-mail e senha · Sprint 4 CI/CD"**.

### Cena 13 · SELECT inicial no banco (~1 min)

SQL Developer, na seção *ESTADO INICIAL* do [`EVIDENCIAS_VIDEO.sql`](EVIDENCIAS_VIDEO.sql):
- A primeira consulta prova que o banco é o container na Azure (`SandboxHost-...`), e não um
  banco local
- O total de tabelas criadas pelo Flyway
- Os tutores da carga inicial

### Cena 14 · CRUD do tutor (~4 min) 🔵 cada operação com SELECT

Duas janelas lado a lado: terminal e SQL Developer. Os comandos estão no
[`EVIDENCIAS_VIDEO.sql`](EVIDENCIAS_VIDEO.sql), na ordem; este roteiro só narra.

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

### Cena 15 · Encerramento (~30 s)

Recapitule: alteração de código na `main` → CI com build, testes e artefato → CD com deploy
automático → aplicação atualizada → dados persistidos no Oracle em nuvem.

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
| Cenas 1–3 (abertura, ferramentas e projeto) | ~4 min |
| Cenas 4–5 (configuração das pipelines) | ~4 min |
| Cenas 6–7 (alteração no código) | ~3 min |
| Cenas 8–10 (CI e CD rodando) | ~7 min com a espera cortada |
| Cenas 11–12 (portal e app) | ~2 min |
| Cenas 13–15 (banco, CRUD e fecho) | ~6 min |

Total: **25 a 30 minutos**. O enunciado não impõe limite, mas pede para evitar excesso.

---

## Conferência final antes de publicar

- [ ] 720p ou superior
- [ ] Áudio audível do início ao fim
- [ ] **Nenhuma legenda** ativada
- [ ] Nenhuma senha visível em nenhum frame
- [ ] A alteração é no **código** (`login.html`), não no README
- [ ] O CI aparece disparando **sozinho** depois do merge na `main`
- [ ] O CD aparece disparando **sozinho** depois do CI
- [ ] Cada etapa do CI e do CD foi explicada
- [ ] A aba Tests e o artefato aparecem
- [ ] Os recursos aparecem no Portal
- [ ] O app aparece com a alteração
- [ ] Cada operação do CRUD tem o seu `SELECT`
- [ ] Publicado como **não listado** e o link testado numa janela anônima
