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

## Antes de gravar

- [ ] **Infra ligada e carga inicial feita**, como no "Antes de gravar" do roteiro de DevOps:
      `98_retomar.sh`, health `UP` e `08_bootstrap.sh`. A coleção faz login com o master,
      que o bootstrap cria.
- [ ] **Postman** aberto, com a coleção
      `tests/postman/petflow-testes-automatizados.postman_collection.json` e o ambiente
      `tests/postman/petflow-azure.postman_environment.json` importados
- [ ] Ambiente **PetFlow - Azure Web App** selecionado, com a senha do master no
      **Current value** de `senhaColaborador`. Não exporte o ambiente de volta para o
      repositório:

      ```bash
      az keyvault secret show --vault-name kv-petflow-s4b-rm561940 --name master-password --query value -o tsv
      ```
- [ ] Se for usar o Newman: `npx newman --version` já executado uma vez, porque a primeira
      execução baixa o pacote
- [ ] Fonte grande, notificações desligadas, microfone testado

### ⚠️ Dois cuidados durante a gravação

**Nenhuma senha na tela.** Preencha `senhaColaborador` antes de gravar. No Postman, o
campo é do tipo *secret* e aparece mascarado.

**Limite de login:** no máximo 5 por minuto por IP. Cada execução da coleção faz 2 logins.
Não rode mais de duas vezes no mesmo minuto, e espere 1 minuto antes de começar o vídeo de
DevOps.

---

## Roteiro

### Cena 1 · Abertura (~1 min)

Diga em voz:
- Que o vídeo é da **Parte B** de Compliance: automação dos testes de integração da API
- Por que **Postman**: o sistema é uma API REST, e o enunciado indica Postman, JMeter ou
  equivalente para sistemas compostos por APIs
- Que os testes rodam contra a aplicação **publicada no Azure**, e não em localhost

### Cena 2 · Configuração da coleção (~3 min)

No Postman:
1. A **coleção** e as 5 pastas, CA01 a CA05. Use a tabela acima para dizer o que cada uma
   verifica.
2. As **variáveis da coleção** e o **pre-request** da coleção: cada execução gera um e-mail
   e uma senha únicos para o tutor de teste.
3. O **ambiente** *PetFlow - Azure Web App*: `baseUrl` aponta para o Web App, e a senha do
   master está mascarada.
4. A aba **Tests** de duas requisições, lendo as asserções em voz alta:
   - **CA01 → Login do colaborador**: HTTP 200, emite `accessToken`, colaborador não recebe
     `refreshToken`, perfil COLABORADOR
   - **CA03 → Recusar exclusão de tutor com pet vinculado**: HTTP 409, nunca 500, e a
     mensagem de conflito de integridade
5. Uma requisição que **guarda dado para a próxima**: o `POST /api/tutores` grava o
   `tutorId` usado nos passos seguintes.

### Cena 3 · Execução (~2 min)

**Opção A, no Postman:** clique na coleção → **Run collection** → mantenha a ordem → **Run**.

**Opção B, na linha de comando** (gera o relatório JUnit):

```bash
npx newman run tests/postman/petflow-testes-automatizados.postman_collection.json \
  -e tests/postman/petflow-azure.postman_environment.json \
  --env-var "senhaColaborador=${SENHA_MASTER}" \
  -r cli,junit --reporter-junit-export tests/postman/resultados/newman-junit.xml
```

Na opção B, defina `SENHA_MASTER` **antes de gravar** (comando do
[`ENTREGA.md`](ENTREGA.md)) e limpe a tela.

### Cena 4 · Resultados (~2 min)

- O resumo final: **13 requisições e 28 asserções aprovadas, 0 falhas**
- Abra uma requisição no resultado e mostre a resposta e as asserções verdes
- Na opção B: a tabela final do Newman e o arquivo `newman-junit.xml` em
  `tests/postman/resultados/`

Se quiser mostrar que os testes **detectam** falha de verdade: troque o `baseUrl` do
ambiente por uma URL errada, rode só o CA01 e mostre as asserções vermelhas. Depois
desfaça a troca.

### Cena 5 · Encerramento (~30 s)

Recapitule: 5 casos e 28 asserções cobrindo saúde, autenticação, cadastro, validação,
integridade, controle de acesso e exclusão, contra a aplicação publicada no Azure.

---

## Depois de gravar

1. Siga para o vídeo de DevOps, esperando 1 minuto por causa do limite de login.
2. Com o link do YouTube: atualize o [`ENTREGA.md`](ENTREGA.md) na branch **`develop`**.

---

## Duração estimada

| Bloco | Tempo |
|---|---|
| Cenas 1–2 (abertura e configuração) | ~4 min |
| Cenas 3–4 (execução e resultados) | ~4 min |
| Cena 5 (fecho) | ~30 s |

Total: **8 a 10 minutos**.

---

## Conferência final antes de publicar

- [ ] Aparecem a **configuração**, a **execução** e os **resultados**, as três coisas que o
      enunciado pede
- [ ] Os 5 casos aparecem, acima do mínimo de 4
- [ ] Nenhuma senha visível em nenhum frame
- [ ] Áudio audível do início ao fim
- [ ] Publicado como **não listado** e o link testado numa janela anônima
