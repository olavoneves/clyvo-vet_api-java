# Clyvo Vet — API Java

API REST de gestão veterinária desenvolvida em Spring Boot 4, com autenticação JWT manual, banco Oracle e suporte a deploy via Docker, Render e Azure VM.

> Projeto acadêmico — FIAP 2TDSR · Challenge 2026

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Banco de dados | Oracle (FIAP) / Oracle XE 21c (local) |
| Autenticação | JWT manual — JJWT 0.12.6 (HS256) |
| Criptografia | BCrypt via spring-security-crypto |
| Documentação | SpringDoc OpenAPI 2.8.9 (Swagger UI) |
| Build | Maven 3.9 (wrapper `mvnw`) |
| Containerização | Docker multi-stage + Docker Compose |

---

## Estrutura de Pacotes

```
br.com.clyvovet.server
├── auth/           # Login, RefreshToken, JwtService, AuthService
├── config/         # AppConfig, AuthInterceptor, WebConfig
├── exception/      # GlobalExceptionHandler, exceções customizadas
├── converter/      # SimNaoConverter (Boolean ↔ 'S'/'N')
├── enums/          # Todos os enums do domínio
├── clinica/
├── veterinario/
├── tutor/
├── especie/
├── raca/
├── pet/
├── consulta/
├── agendamento/
├── anamnese/
├── prescricao/
├── medicamento/
├── exame/
├── vacinacao/
├── fichaclinica/   # alergia/, condicao/
├── tipoalergia/
├── tipocondicao/
├── tipovacina/
├── tiposensor/
├── iot/            # sensor/, leitura/, alerta/
└── auditoria/      # LogErro
```

---

## Banco de Dados

24 tabelas Oracle com prefixo `TB_CLV_`. Schema gerenciado manualmente pelo DBA (`ddl-auto=none`).

### Scripts de migração (`docs/migration/`)

| Arquivo | Finalidade | Quando usar |
|---|---|---|
| `V_COMPLETO__create_all_tables.sql` | DDL completo (todas as tabelas + FKs + constraints) | Ambiente local — fresh install |
| `V0__foreign_keys.sql` | Apenas FKs via `ALTER TABLE` | Banco FIAP — já tem tabelas, só falta FKs |
| `V1__veterinario_auth_e_refresh_token.sql` | Adiciona `ds_email`/`ds_senha_hash` no VETERINÁRIO + cria `TB_CLV_REFRESH_TOKEN` | Banco FIAP — após V0 |

**Ordem no banco FIAP:** `V0` → `V1` (antes de subir a aplicação pela primeira vez)

### Diagrama de dependências (simplificado)

```
ESPECIE → RACA → PET ← TUTOR
CLINICA → VETERINARIO ↗
PET + VETERINARIO → CONSULTA
CONSULTA → ANAMNESE (1:1)
CONSULTA → PRESCRICAO ← MEDICAMENTO
CONSULTA → EXAME
PET + TIPO_VACINA + VET → APLICACAO_VACINA
PET + TIPO_ALERGIA → ALERGIA_PET
PET + TIPO_CONDICAO → CONDICAO_PET
PET + TIPO_SENSOR → SENSOR_IOT → LEITURA_IOT → ALERTA_IOT
```

---

## Autenticação

### Fluxo

```
POST /auth/login
  Body: { email, senha, tipo: "TUTOR" | "VETERINARIO" }
  Response: { accessToken, refreshToken, tipo, id, nome, email }

POST /auth/refresh
  Body: { refreshToken }
  Response: { accessToken, refreshToken, tipo, id, nome, email }

POST /auth/logout
  Body: { refreshToken }
  Response: 204 No Content
```

O `accessToken` expira em **15 minutos**. O `refreshToken` em **7 dias**.

### Rotas públicas (sem token)

| Rota | Descrição |
|---|---|
| `POST /auth/login` | Login |
| `POST /auth/refresh` | Renovar token |
| `POST /auth/logout` | Revogar sessão |
| `POST /tutores` | Cadastro de tutor |
| `POST /clinicas` | Cadastro de clínica |
| `POST /veterinarios` | Cadastro de veterinário |
| `GET /swagger-ui.html` | Documentação Swagger |
| `GET /v3/api-docs/**` | OpenAPI spec |

Todas as demais rotas exigem `Authorization: Bearer <accessToken>`.

---

## Rodando Localmente

### Com Docker Compose (recomendado)

Sobe Oracle XE 21c + API automaticamente:

```bash
docker-compose up --build
```

Aguarde o Oracle ficar healthy (~2 min na primeira vez). Depois execute o script DDL:

```
Conecte no Oracle local: jdbc:oracle:thin:@localhost:1521/PETFLOWDB
Usuário: petflow  |  Senha: PetFlow2026
Execute: docs/migration/V_COMPLETO__create_all_tables.sql
```

API disponível em: `http://localhost:8080`  
Swagger: `http://localhost:8080/swagger-ui.html`

### Sem Docker (banco FIAP)

1. Configure as credenciais em `application.properties` ou via variáveis de ambiente
2. Execute as migrations V0 → V1 no banco FIAP
3. Rode com o Maven wrapper:

```bash
./mvnw spring-boot:run        # Linux/macOS
.\mvnw.cmd spring-boot:run    # Windows
```

---

## Variáveis de Ambiente

| Variável | Descrição | Default (application.properties) |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC do Oracle | `jdbc:oracle:thin:@oracle.fiap.com.br:1521:ORCL` |
| `SPRING_DATASOURCE_USERNAME` | Usuário Oracle | `rm563558` |
| `SPRING_DATASOURCE_PASSWORD` | Senha Oracle | — |
| `APP_JWT_SECRET` | Secret HS256 (mín. 32 chars) | valor padrão de dev |
| `PORT` | Porta HTTP | `8080` |

---

## Deploy

### Render

1. Conecte o repo no [render.com](https://render.com)
2. Render detecta o `render.yaml` automaticamente
3. Configure as 4 variáveis de ambiente no dashboard
4. Branch de deploy: `main`

### Azure VM

```bash
git clone https://github.com/olavoneves/clyvo-vet_api-java.git
cd clyvo-vet_api-java/server
docker-compose up -d --build
```

---

## Git Flow

```
main      ← produção (nunca commitar direto)
release   ← homologação / testes
develop   ← desenvolvimento
```

**Convenção de commits:** `feature:` · `fix:` · `refactor:` · `docs:` · `test:` · `chore:`

---

## Screenshots

> Adicione os prints na pasta `docs/images/` e referencie abaixo.

### Swagger UI

![Swagger UI](docs/images/swagger-ui.png)

### Login — POST /auth/login

![Login](docs/images/auth-login.png)

### Exemplo de listagem paginada

![Listagem](docs/images/listagem-paginada.png)

### Exemplo de erro (401 Unauthorized)

![401](docs/images/erro-401.png)

---

## Documentação completa

Acesse o Swagger após subir a aplicação:

```
http://localhost:8080/swagger-ui.html
```
