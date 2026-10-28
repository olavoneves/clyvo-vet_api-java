# Roteiro do vídeo — Sprint 4 · Compliance, Quality Assurance & Tests

Vídeo da **Parte B (automação de teste)**: o enunciado pede *"ao menos 4 casos de testes
automatizados"* e um vídeo *"mostrando a configuração, execução e resultados dos testes"*.

A **Parte A** (plano de testes manuais) **não tem vídeo**: é corrigida direto no Azure Boards,
pelo link do [`ENTREGA.md`](ENTREGA.md).

> Gravado na **mesma sessão** do vídeo de DevOps
> ([`../ROTEIRO_VIDEO.md`](../ROTEIRO_VIDEO.md)): a infra é ligada uma vez só. Este vem
> **primeiro**, porque é mais curto e porque o de DevOps altera e republica a aplicação.

---

## O que a coleção cobre

5 casos, 13 requisições, **28 asserções**, executados contra o Azure Web App publicado.

| Caso | Requisições | O que as asserções verificam |
|---|---|---|
| **CA01** Saúde e autenticação | `GET /actuator/health`, `POST /api/auth/login` | 200 e `UP` com o Oracle; login do colaborador com `accessToken`, sem `refreshToken`, perfil COLABORADOR |
| **CA02** Cadastro e alteração | `POST /api/tutores`, `POST /api/pets`, `PUT /api/pets/{id}` | 201 com o id gerado, pet vinculado ao tutor e ATIVO, nome alterado |
| **CA03** Validação e integridade | `POST /api/pets` inválido, `DELETE /api/tutores/{id}` | **422** apontando os campos obrigatórios; **409, nunca 500**, ao excluir tutor com pet |
| **CA04** Controle de acesso | login do tutor, `GET /api/tutores`, `GET /api/tutor/pets` | perfil TUTOR com `refreshToken`; **403** na API de gestão; tutor vê só o próprio pet |
| **CA05** Exclusão | `DELETE` do pet, `DELETE` do tutor, `GET` do tutor | 204, 204 e **404** depois |

Cada execução cria um tutor com **e-mail e senha únicos** e apaga tudo o que criou: pode
ser repetida sem preparar dados.

---
## Ferramenta: Newman

Os testes rodam no **Newman**, o executor oficial de coleções do Postman por linha de comando.
É o mesmo motor do Postman e roda a mesma coleção, sem instalar o Postman: basta o Node.js e o
`npx`. O enunciado aceita *"POSTMAN, JMETER ou software equivalente"*.

O vídeo cobre só as três coisas que o enunciado pede: **configuração**, **execução** e
**resultados**.

---

## Antes de gravar

Tudo isso fica **fora** do vídeo.

- [ ] **Infra ligada, CD rodado e carga inicial feita**, como no "Antes de gravar" do roteiro
      de DevOps: `98_retomar.sh`, reativar o CD, rodar o CD manualmente, health `UP` e
      `08_bootstrap.sh`. A coleção faz login com o master, que o bootstrap cria.
- [ ] Newman já baixado (a primeira execução baixa o pacote):

      ```bash
      npx --yes newman --version      # 6.2.3 ou superior
      ```
- [ ] Terminal Git Bash **na raiz do repositório**, com a senha do master numa variável e a
      tela limpa:

      ```bash
      SENHA_MASTER=$(az keyvault secret show --vault-name kv-petflow-s4b-rm561940 \
        --name master-password --query value -o tsv | tr -d '\r')
      echo ${#SENHA_MASTER}     # deve imprimir 28, nao 0
      clear
      ```
- [ ] IDE com os três arquivos abertos em abas:
      `tests/postman/gerar_colecao.py`,
      `tests/postman/petflow-testes-automatizados.postman_collection.json` e
      `tests/postman/petflow-azure.postman_environment.json`
- [ ] Fonte grande no terminal e na IDE, notificações desligadas, microfone testado

### ⚠️ Dois cuidados durante a gravação

**Nenhuma senha na tela.** O `SENHA_MASTER` é preenchido no preparo, antes de gravar. No
comando do vídeo aparece só o nome da variável, nunca o valor. O arquivo de ambiente não tem
senha: `senhaColaborador` está vazio de propósito.

**Limite de login:** no máximo 5 por minuto por IP, e cada execução da coleção faz 2 logins.
Não rode mais de duas vezes no mesmo minuto, e espere 1 minuto antes de começar o vídeo de
DevOps.

---

## Ensaio antes de gravar (recomendado)

Rode a coleção uma vez **sem gravar**, para conferir que tudo passa. É o mesmo comando da
Cena 2:

```bash
npx newman run tests/postman/petflow-testes-automatizados.postman_collection.json \
  -e tests/postman/petflow-azure.postman_environment.json \
  --env-var "senhaColaborador=${SENHA_MASTER}" \
  -r cli,junit --reporter-junit-export tests/postman/resultados/newman-junit.xml
```

O esperado no fim: `requests 13 | 0` e `assertions 28 | 0` na coluna *failed*. O ensaio pode ser
repetido à vontade, porque cada execução cria e apaga os próprios dados. Depois dele, **espere
1 minuto** antes de gravar (limite de login) e rode `clear`.

---

## Roteiro

> **Não é preciso rodar o `gerar_colecao.py`.** Ele foi usado uma vez para gerar a coleção
> (`.json`), que já está pronta no repositório. No vídeo, ele só é **mostrado**, porque nele os
> testes estão escritos de forma legível.

### Introdução (~20 s)

Com a IDE na tela, já com a aba do `gerar_colecao.py` aberta, diga:

> "Este vídeo é da **Parte B de Compliance, Quality Assurance & Tests**: a automação dos testes
> do **PetFlow**, a API da Clyvo Vet. São **5 casos de teste automatizados**, rodados com o
> **Newman**, o executor de coleções do Postman, contra a aplicação **publicada no Azure**.
> Vou mostrar a configuração, a execução e os resultados."

### Cena 1 · Configuração dos testes (~3 min) 🔵 "configuração"

**Passo 1. Os 5 casos de teste.**
Abra a aba `tests/postman/gerar_colecao.py` e vá até a **linha 84**. Desça devagar até a
**linha 168**, parando em cada caso:

| Linha | Caso | O que falar |
|---|---|---|
| 84 | `CA01 - Saude e autenticacao` | "Confere se a aplicação está no ar, ligada ao banco, e se o login do colaborador funciona" |
| 103 | `CA02 - Cadastro e alteracao de tutor e pet` | "Cadastra um tutor, um pet desse tutor e altera o pet" |
| 129 | `CA03 - Validacao e integridade` | "Envia dados inválidos e tenta excluir um tutor que tem pet" |
| 145 | `CA04 - Controle de acesso por perfil` | "Confere que o tutor só acessa o que é dele" |
| 168 | `CA05 - Exclusao na ordem correta` | "Exclui o pet, depois o tutor, e confere que ele sumiu" |

Diga: "são 5 casos, com 13 requisições e 28 asserções; o enunciado pede no mínimo 4".

**Passo 2. As asserções de uma requisição de sucesso.**
Volte para a **linha 90**, `requisicao("Login do colaborador", ...)`. Selecione as linhas
**93 a 97** e leia cada `pm.test` em voz alta:

- `HTTP 200`: o login respondeu com sucesso
- `emite accessToken`: a resposta traz o token de acesso
- `colaborador nao recebe refreshToken`: regra de segurança do perfil colaborador
- `perfil COLABORADOR`: o token é do perfil certo

Aponte a **linha 98**: o token fica guardado em `token` e é usado nas requisições seguintes.

**Passo 3. As asserções de uma requisição de erro esperado.**
Vá para a **linha 138**, `requisicao("Recusar exclusao de tutor com pet vinculado", ...)`, e
leia as **linhas 139 e 140**:

- `HTTP 409, nunca 500`: tentar excluir um tutor que tem pet é recusado com conflito, e não
  quebra a aplicação
- `conflito de integridade`: a resposta explica o motivo (`Data Conflict`)

Diga: "os testes também verificam os erros esperados, não só os casos de sucesso".

**Passo 4. A coleção gerada e os dados de teste.**
Abra a aba `tests/postman/petflow-testes-automatizados.postman_collection.json` e mostre as
**linhas 8 a 20**, o *pre-request* da coleção. Aponte as **linhas 16 e 17**:

- linha 16: gera um **e-mail único** (`qa.auto.<horário>@exemplo.com`)
- linha 17: gera uma **senha aleatória**

Diga: "antes de rodar, a coleção cria um tutor com e-mail e senha únicos; no fim, ela mesma
apaga o que criou. Por isso pode rodar quantas vezes quiser, sem preparar dados".

**Passo 5. O ambiente.**
Abra a aba `tests/postman/petflow-azure.postman_environment.json` (são só 9 linhas):

- **linha 4**, `baseUrl`: "os testes rodam contra a aplicação publicada no Azure, e não em
  localhost"
- **linha 6**, `senhaColaborador`: "está **vazio** de propósito: a senha não fica no
  repositório; ela entra na hora da execução, vinda do Azure Key Vault"

### Cena 2 · Execução (~2 min) 🔵 "execução"

Vá para o terminal (já limpo) e cole o comando:

```bash
npx newman run tests/postman/petflow-testes-automatizados.postman_collection.json \
  -e tests/postman/petflow-azure.postman_environment.json \
  --env-var "senhaColaborador=${SENHA_MASTER}" \
  -r cli,junit --reporter-junit-export tests/postman/resultados/newman-junit.xml
```

**Antes de apertar Enter**, explique cada parte:

| Parte | O que falar |
|---|---|
| `npx newman run <coleção>` | "O Newman é o executor oficial de coleções do Postman por linha de comando" |
| `-e <ambiente>` | "Usa o ambiente que aponta para o Azure" |
| `--env-var "senhaColaborador=${SENHA_MASTER}"` | "A senha do master entra aqui, vinda do Key Vault; só o nome da variável aparece na tela" |
| `-r cli,junit ...` | "Gera o resultado aqui no terminal e também um relatório JUnit em arquivo" |

Aperte **Enter**. Leva uns 12 segundos. Enquanto roda, acompanhe a saída: cada requisição
aparece com o método, a URL do Azure, o status HTTP (`200 OK`, `201 Created`, `409 Conflict`,
`404 Not Found`) e, embaixo, cada asserção com **√**.

Aponte dois momentos:
- em **CA03**, o `409 Conflict` com as asserções verdes: o erro esperado aconteceu
- em **CA05**, o `404 Not Found` no fim: o tutor foi mesmo excluído

### Cena 3 · Resultados (~2 min) 🔵 "resultados"

**Passo 1. A tabela final no terminal.** Aponte cada linha, mostrando a coluna *failed*:

| Linha | Esperado | O que falar |
|---|---|---|
| `requests` | 13 executadas, **0 falhas** | "todas as requisições responderam" |
| `test-scripts` | 13, **0 falhas** | "os scripts de teste rodaram sem erro" |
| `assertions` | 28, **0 falhas** | "as 28 verificações passaram" |

E o `total run duration`, cerca de 12 segundos.

**Passo 2. O relatório JUnit.** Na IDE, abra `tests/postman/resultados/newman-junit.xml`
(gerado agora pela execução):

- **linha 2**, `<testsuites ... tests="13">`: as 13 requisições
- logo abaixo, cada `<testsuite name="CA01 - ... / ...">` é uma requisição, com
  `failures="0"`, e cada `<testcase>` dentro dela é uma asserção
- use **Ctrl+F** e procure `failure`: só aparecem os `failures="0"`, nenhuma tag de falha

Diga: "este é o formato padrão de relatório de testes, que ferramentas de CI usam para
publicar resultados".

---

## Depois de gravar

1. Espere 1 minuto (limite de login) e siga para o vídeo de DevOps.
2. Com o link do YouTube: atualize o [`ENTREGA.md`](ENTREGA.md) na branch **`develop`**.

---

## Duração estimada

| Bloco | Tempo |
|---|---|
| Introdução | ~20 s |
| Cena 1 (configuração) | ~3 min |
| Cena 2 (execução) | ~2 min |
| Cena 3 (resultados) | ~2 min |

Total: **cerca de 7 minutos**.

---

## Conferência final antes de publicar

- [ ] Aparecem a **configuração**, a **execução** e os **resultados**, as três coisas que o
      enunciado pede
- [ ] Os 5 casos aparecem, acima do mínimo de 4
- [ ] A tabela final mostra **0 falhas**
- [ ] Nenhuma senha visível em nenhum frame
- [ ] Áudio audível do início ao fim
- [ ] Publicado como **não listado** e o link testado numa janela anônima

---

> **Alternativa sem Newman:** o Postman no navegador (`web.postman.co`, com conta gratuita)
> importa a mesma coleção e o mesmo ambiente e roda pelo *Run collection*. Nesse caso,
> preencha `senhaColaborador` no **Current value** do ambiente antes de gravar, sem exportar
> o ambiente de volta para o repositório.
