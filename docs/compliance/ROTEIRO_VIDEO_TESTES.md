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

## Roteiro

### Cena 1 · Configuração dos testes (~3 min) 🔵 "configuração"

Na IDE, em três passos:

1. **`gerar_colecao.py`: os testes escritos como código.** A coleção é gerada por este script,
   para as asserções poderem ser revisadas como código. Mostre:
   - os **5 casos**, CA01 a CA05, e diga o que cada um verifica (tabela acima);
   - as asserções de **duas** requisições, lidas em voz alta:
     - **CA01 → "Login do colaborador"**: HTTP 200, emite `accessToken`, colaborador não
       recebe `refreshToken`, perfil COLABORADOR;
     - **CA03 → "Recusar exclusão de tutor com pet vinculado"**: HTTP **409, nunca 500**, e
       a mensagem de conflito de integridade.
2. **`petflow-testes-automatizados.postman_collection.json`: a coleção gerada.** Mostre o
   **pre-request** no topo: cada execução gera um e-mail e uma senha únicos para o tutor de
   teste. Por isso a coleção pode rodar várias vezes sem preparar dados.
3. **`petflow-azure.postman_environment.json`: o ambiente.** O `baseUrl` aponta para o Web App
   publicado no Azure, e o `senhaColaborador` está **vazio**: a senha do master não fica no
   repositório e entra na hora da execução, vinda do Key Vault.

### Cena 2 · Execução (~2 min) 🔵 "execução"

No terminal, na raiz do repositório:

```bash
npx newman run tests/postman/petflow-testes-automatizados.postman_collection.json \
  -e tests/postman/petflow-azure.postman_environment.json \
  --env-var "senhaColaborador=${SENHA_MASTER}" \
  -r cli,junit --reporter-junit-export tests/postman/resultados/newman-junit.xml
```

Explique o comando em uma frase por parte: a **coleção** (`run`), o **ambiente** (`-e`), a
**senha** vinda da variável (`--env-var`) e os **relatórios** (`-r`: no terminal e em JUnit).

Enquanto roda, acompanhe a saída: cada requisição aparece com o método, a rota, o status HTTP
e, embaixo, cada asserção com ✓.

### Cena 3 · Resultados (~2 min) 🔵 "resultados"

1. **Tabela final do Newman:** 13 requisições e **28 asserções, 0 falhas**. Aponte as linhas
   `requests`, `test-scripts` e `assertions`.
2. **Relatório JUnit:** abra na IDE o `tests/postman/resultados/newman-junit.xml`. Cada requisição
   aparece como `testsuite`, com as suas asserções como `testcase` e nenhum `failure`. É o mesmo
   formato que ferramentas de CI usam para publicar resultados de testes.

---

## Depois de gravar

1. Espere 1 minuto (limite de login) e siga para o vídeo de DevOps.
2. Com o link do YouTube: atualize o [`ENTREGA.md`](ENTREGA.md) na branch **`develop`**.

---

## Duração estimada

| Bloco | Tempo |
|---|---|
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
