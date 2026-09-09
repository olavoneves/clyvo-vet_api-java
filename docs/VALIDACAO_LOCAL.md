# Validação local — Fase 4 (bloqueante)

**Data:** 2026-09-09 · **Branch:** `devops/sprint3` · **Base:** `393a2ea`

Nada sobe para a Azure sem estes critérios passarem. Executado com Docker local,
banco Oracle XE **vazio** e sem volume — a mesma topologia que o ACI terá.

---

## Ambiente

| Item | Valor |
|---|---|
| Imagem do banco | `rm561940-db-petflow:v1` (a partir de `db/Dockerfile`) |
| Imagem do app | `rm561940-app-petflow:v1` (multi-stage do `Dockerfile` da raiz) |
| Rede | `petflow-net` |
| Volume | **nenhum** — disco efêmero, como será no ACI |
| Senhas | geradas em runtime (`openssl rand`), gravadas só em `.env.local` (`umask 077`, no `.gitignore`) |

---

## Critérios de aceite

| # | Critério | Resultado |
|---|---|---|
| 1 | Banco chega em `DATABASE IS READY TO USE!` sem `ORA-01990` | ✅ 134s, zero ocorrências |
| 2 | Usuário `petflow` existe e autentica | ✅ |
| 3 | 🔵 `docker exec app-local id` sem `uid=0(root)` | ✅ `uid=999(appuser)` |
| 4 | Flyway aplica a cadeia completa | ✅ 15 migrations, V0 → V13 |
| 5 | Tabelas criadas | ✅ 37 tabelas |
| 6 | 🔵 CRUD completo em `/api/tutores` | ✅ POST 201, GET 200, PUT 200, DELETE 204 |
| 7 | 🔵 CRUD completo em `/api/pets` | ✅ POST 201, GET 200, PUT 200, DELETE 204 |
| 8 | `DELETE /api/tutores/{id}` com pet vinculado → 409 | ✅ **409**, não 500 |
| 9 | Segunda subida não reaplica migrations | ✅ restart limpo |
| 10 | 🔵 `grep -ni "h2" pom.xml` | ✅ nenhuma ocorrência |

---

## 1. O `ORA-01990` era do CIFS — confirmado

O spike de 25–26/08 deixou em aberto se o `ORA-01990` vinha do mount do Azure Files.
**Vinha.** Sem volume, o boot é limpo:

```
DATABASE IS READY TO USE!   (134s)
ORA-01990: 0 ocorrências
```

Isso valida a decisão de dispensar Azure Files e Storage Account.

## 2. O `baseline-version=8` não atrapalha banco vazio

`application.properties` traz `baseline-on-migrate=true` e `baseline-version=8`,
configurados para o banco da FIAP, onde o schema já existe. A dúvida era se, num banco
vazio, o Flyway marcaria o schema como "já na V8" e pularia V0–V8.

**Não pula.** O log responde:

```
All configured schemas are empty; baseline operation skipped.
Current version of schema "PETFLOW": << Empty Schema >>
Migrating schema "PETFLOW" to version "0 - baseline schema"
...
Successfully applied 15 migrations to schema "PETFLOW", now at version v13
```

`baseline-on-migrate` só age quando há objetos no schema. **Nenhum ajuste é necessário
nos scripts de deploy** — o `application.properties` serve aos dois cenários.

## 3. `ORA-17110` na V8 é warning, não erro

Duas ocorrências, ambas `WARN` do `DefaultSqlScriptExecutor` na V8, que compila objetos
PL/SQL. As migrations seguiram e as 15 foram aplicadas. Não bloqueia o boot.

---

## Bootstrap: a ordem importa

Num banco vazio o schema nasce completo, mas **sem dados**. As procedures
`PR_CLV_SEED_*` da V8 não são chamadas pela aplicação e **falham se não houver clínica**:

```
ORA-01400: cannot insert NULL into ("PETFLOW"."TB_CLV_TUTOR"."ID_CLINICA")
```

A causa está na própria procedure: `SELECT MIN(id_clinica) INTO v_clinica FROM TB_CLV_CLINICA`
devolve `NULL` quando a tabela está vazia.

O `DataInitializer` também depende de clínica — sem ela, registra:

```
Nenhuma clinica cadastrada: usuario master nao criado.
Cadastre uma clinica (POST /clinicas) e reinicie a aplicacao.
```

### Sequência que funciona

```bash
# 1. Criar a clínica (rota pública, sem token)
curl -X POST http://<FQDN>:8080/api/clinicas \
  -H "Content-Type: application/json" \
  -d '{"nome":"Clinica PetFlow Central","cnpj":"12345678000199",
       "logradouro":"Av Paulista","numero":"1000","bairro":"Bela Vista",
       "cidade":"Sao Paulo","estado":"SP","cep":"01310100","telefone":"1133334444"}'

# 2. Reiniciar o app — o DataInitializer cria o master
#    log esperado: "Usuario master criado na clinica 1"

# 3. Login
curl -X POST http://<FQDN>:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"master@clyvovet.com","senha":"master","tipo":"COLABORADOR"}'
```

> **Isto é requisito de arquitetura do deploy, não detalhe operacional.** Precisa estar
> no README e no roteiro do vídeo. Sem volume, recriar o ACI do banco zera os dados e
> exige repetir a sequência.

---

## Correções à premissa do roteiro

**As rotas vivem sob `/api`.** O roteiro v2 (§2.3) dizia que a API não usa prefixo. Usa —
aplicado pelo `WebMvcConfig`, não escrito nos controllers. As rotas corretas são
`/api/tutores` e `/api/pets`. Isso vale para o smoke test, o README e o vídeo.

**A variável do JWT mudou de nome:** era `APP_JWT_SECRET`, agora é **`JWT_SECRET`**.

**São quatro variáveis obrigatórias** (as demais têm default):

```
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD   ← secreta
JWT_SECRET                   ← secreta
```

`GEMINI_API_KEY` e `ANTHROPIC_API_KEY` têm default vazio (`${VAR:}`) e não bloqueiam o
boot — o agente LLM fica inativo sem elas.

**Duas variáveis secretas para o app**, não uma. Reforça a decisão de usar Key Vault:
sem ele, as senhas precisariam trafegar em arquivo entre os scripts `05` e `06`.

---

## Evidência do CRUD

```
POST /api/clinicas                      201   (público, sem token)
POST /api/auth/login                    200   JWT emitido
POST /api/tutores                       201   id=3
GET  /api/tutores                       200
GET  /api/tutores/3                     200
PUT  /api/tutores/3                     200
POST /api/pets                          201   id=1, tutorId=3
PUT  /api/pets/1                        200
DELETE /api/tutores/3  (com pet)        409   ← integridade referencial
DELETE /api/pets/1                      204
GET  /api/pets/1                        404
DELETE /api/tutores/3                   204
GET  /api/tutores/3                     404
```

> `PUT /api/tutores/{id}` reusa o DTO do POST: exige `senha` e `clinicaId` no corpo,
> além dos campos alterados. Um PUT parcial devolve **422**.
