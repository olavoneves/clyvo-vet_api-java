# 🐾 PetFlow — Clyvo Vet API Java

**Infraestrutura do futuro da medicina veterinária digital**

Plataforma de saúde animal que transforma a jornada do pet de um modelo episódico e reativo para uma experiência contínua, preventiva, inteligente e integrada. Desenvolvida como parte do Challenge Clyvo Vet — FIAP 2025/2026.

> Projeto acadêmico — FIAP 2TDSR · Challenge 2026

---

## 📋 Índice

- [Descrição do Projeto](#-descrição-do-projeto)
- [Stack](#-stack)
- [Estrutura de Pacotes](#-estrutura-de-pacotes)
- [Banco de Dados](#-banco-de-dados)
- [Autenticação](#-autenticação)
- [Rotas da API](#-rotas-da-api)
- [Rodando Localmente](#-rodando-localmente)
- [Variáveis de Ambiente](#-variáveis-de-ambiente)
- [Deploy — Render](#-deploy--render)
- [Git Flow](#-git-flow)
- [Screenshots](#-screenshots)
- [Benefícios para o Negócio](#-benefícios-para-o-negócio)
- [Arquitetura Macro](#-arquitetura-macro)
- [Instalação da Solução — Azure VM (How to)](#-instalação-da-solução--azure-vm-how-to)
- [Scripts DevOps](#-scripts-devops)
- [Equipe](#-equipe)

---

## 📖 Descrição do Projeto

O **PetFlow** é uma API REST desenvolvida em **Java 21** com **Spring Boot 4.0.6**, conectada a um banco **Oracle XE 21c**, ambos conteinerizados com Docker e provisionados na **Microsoft Azure** via scripts automatizados com Azure CLI, seguindo práticas de Infrastructure as Code (IaC).

A plataforma conecta tutores de pets, clínicas veterinárias e o ecossistema de saúde animal, oferecendo funcionalidades como:

- Cadastro de pets, tutores, clínicas e veterinários
- Agendamento de consultas e registro de anamneses
- Controle de vacinação e prescrições médicas
- Monitoramento via sensores IoT com alertas inteligentes
- Autenticação JWT (access token 15min + refresh token 7 dias)
- Documentação interativa via Swagger

---

## 🛠️ Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Banco de Dados | Oracle XE 21c (Docker) / Oracle FIAP |
| Autenticação | JWT manual — JJWT 0.12.6 (HS256) |
| Criptografia | BCrypt via spring-security-crypto |
| Documentação | SpringDoc OpenAPI 2.8.9 (Swagger UI) |
| Build | Maven 3.9 (wrapper `mvnw`) |
| Containerização | Docker multi-stage + Docker Compose |
| Infraestrutura | Microsoft Azure, Azure CLI, Ubuntu 22.04 |

---

## 📦 Estrutura de Pacotes

```
br.com.clyvovet.server
├── auth/           # Login, RefreshToken, JwtService, AuthService
├── config/         # AppConfig, AuthInterceptor, WebConfig, DataInitializer
├── exception/      # GlobalExceptionHandler, exceções customizadas
├── converter/      # SimNaoConverter (Boolean ↔ 'S'/'N')
├── enums/          # Todos os enums do domínio
├── colaborador/    # Entidade interna (admin/master)
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

## 🗄️ Banco de Dados

25 tabelas Oracle com prefixo `TB_CLV_`. Schema gerenciado via `ddl-auto=update` no Docker ou manualmente no banco FIAP.

### Scripts de migração (`docs/migration/`)

| Arquivo | Finalidade | Quando usar |
|---|---|---|
| `V_COMPLETO__create_all_tables.sql` | DDL completo (25 tabelas + FKs + constraints) | Docker local — fresh install |
| `V0__foreign_keys.sql` | Apenas FKs via `ALTER TABLE` | Banco FIAP — já tem tabelas, só falta FKs |
| `V1__veterinario_auth_e_refresh_token.sql` | Adiciona campos de auth no VETERINÁRIO + cria `TB_CLV_REFRESH_TOKEN` | Banco FIAP — após V0 |
| `V2__colaborador.sql` | Cria `TB_CLV_COLABORADOR` (usuários internos/admin) | Banco FIAP — após V1 |

**Ordem no banco FIAP:** `V0` → `V1` → `V2` (antes de subir a aplicação pela primeira vez)

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
COLABORADOR (sem FKs — usuário interno)
```

---

## 🔐 Autenticação

### Fluxo

```
POST /auth/login
  Body: { "email": "...", "senha": "...", "tipo": "TUTOR" | "VETERINARIO" | "COLABORADOR" }
  Response: { accessToken, refreshToken, tipo, id, nome, email }

POST /auth/refresh          (não disponível para COLABORADOR)
  Body: { "refreshToken": "..." }
  Response: { accessToken, refreshToken, tipo, id, nome, email }

POST /auth/logout           (não disponível para COLABORADOR)
  Body: { "refreshToken": "..." }
  Response: 204 No Content
```

O `accessToken` expira em **15 minutos**. O `refreshToken` em **7 dias**.

### Usuário master (criado automaticamente na inicialização)

| Campo | Valor |
|---|---|
| email | `master@clyvovet.com` |
| senha | `master` |
| tipo | `COLABORADOR` |

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

## 🔀 Rotas da API

### Rotas protegidas (requerem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/especies` | Listar espécies (paginado) |
| `GET` | `/especies/{id}` | Buscar espécie por ID |
| `POST` | `/especies` | Criar espécie |
| `PUT` | `/especies/{id}` | Atualizar espécie |
| `DELETE` | `/especies/{id}` | Remover espécie |
| `GET` | `/racas` | Listar raças |
| `GET` | `/pets` | Listar pets |
| `GET` | `/pets/{id}/ficha-tecnica` | Ficha técnica completa do pet |
| `GET` | `/consultas` | Listar consultas |
| `GET` | `/agendamentos` | Listar agendamentos |
| `GET` | `/veterinarios` | Listar veterinários |
| `GET` | `/tutores` | Listar tutores |
| `GET` | `/clinicas` | Listar clínicas |
| `GET` | `/vacinas/tipos` | Listar tipos de vacina |
| `GET` | `/vacinas/aplicacoes` | Listar aplicações de vacina |
| `GET` | `/exames` | Listar exames |
| `GET` | `/prescricoes` | Listar prescrições |
| `GET` | `/sensores-iot` | Listar sensores IoT |
| `GET` | `/leituras-iot` | Listar leituras IoT |
| `GET` | `/alertas-iot` | Listar alertas IoT |

A documentação completa está disponível via Swagger em `/swagger-ui.html`.

### Exemplo de uso via curl

```bash
# 1. Criar clínica (público)
curl -X POST http://<IP>:8080/clinicas \
  -H "Content-Type: application/json" \
  -d '{"nome":"CLYVO Vet SP","cnpj":"12345678000199","logradouro":"Av. Paulista 1000","cidade":"Sao Paulo","estado":"SP"}'

# 2. Criar veterinário (público)
curl -X POST http://<IP>:8080/veterinarios \
  -H "Content-Type: application/json" \
  -d '{"nome":"Dr. Paulo Costa","crmv":"SP-67890","email":"paulo@clyvovet.com","senha":"<SUA_SENHA>","clinicaId":1}'

# 3. Login (obter token)
curl -X POST http://<IP>:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"paulo@clyvovet.com","senha":"<SUA_SENHA>","tipo":"VETERINARIO"}'

# 4. Usar token nas rotas protegidas
curl http://<IP>:8080/especies \
  -H "Authorization: Bearer <SEU_TOKEN>"
```

---

## 💻 Rodando Localmente

### Com Docker Compose (recomendado)

```bash
docker compose up --build
```

Aguarde o Oracle ficar healthy (~2 min). A API cria as tabelas automaticamente via `ddl-auto=update`.

API: `http://localhost:8080`  
Swagger: `http://localhost:8080/swagger-ui.html`

### Sem Docker (banco FIAP)

1. Configure as variáveis de ambiente
2. Execute as migrations na ordem: `V0` → `V1` → `V2`
3. Rode:

```bash
./mvnw spring-boot:run        # Linux/macOS
.\mvnw.cmd spring-boot:run    # Windows
```

---

## ⚙️ Variáveis de Ambiente

| Variável | Descrição | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC do Oracle | — (obrigatória) |
| `SPRING_DATASOURCE_USERNAME` | Usuário Oracle | — (obrigatória) |
| `SPRING_DATASOURCE_PASSWORD` | Senha Oracle | — (obrigatória) |
| `APP_JWT_SECRET` | Secret HS256 (mín. 32 chars) | — (obrigatória) |
| `PORT` | Porta HTTP | `8080` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | DDL auto | `none` |

> ⚠️ **Nunca commite credenciais reais.** Use variáveis de ambiente ou arquivo `.env` (que está no `.gitignore`).

---

## 🌐 Deploy — Render

**API em produção:** `https://clyvo-vet-api-java.onrender.com`  
**Swagger:** `https://clyvo-vet-api-java.onrender.com/swagger-ui.html`

1. Conecte o repositório no [render.com](https://render.com)
2. Crie um **Web Service** com runtime **Docker**
3. Branch: `main`
4. Configure as variáveis de ambiente no dashboard

> `PORT` não precisa ser configurada — o Render injeta automaticamente.  
> Plano gratuito hiberna após 15 min de inatividade — primeira requisição pode demorar ~30s.

---

## 🔀 Git Flow

```
main      ← produção (nunca commitar direto)
release   ← homologação / testes
develop   ← desenvolvimento
```

**Convenção de commits:** `feature:` · `fix:` · `refactor:` · `docs:` · `test:` · `chore:`

---

## 📸 Screenshots

### Swagger UI
![Swagger UI](docs/images/swagger-ui.png)

### Login — POST /auth/login
![Login](docs/images/login.png)

### Exemplo de listagem paginada
![Listagem](docs/images/listagem.png)

### Exemplo de erro (401 Unauthorized)
![401](docs/images/401.png)

---
---

# ☁️ DevOps Tools & Cloud Computing

---

## 💼 Benefícios para o Negócio

**Para o tutor do pet:** acesso centralizado ao histórico de saúde, lembretes automáticos de vacinas e consultas, triagem inteligente de sintomas com IA, e maior previsibilidade no cuidado preventivo.

**Para o pet:** melhor cuidado preventivo, detecção precoce de problemas via IoT, maior adesão a tratamentos, e acompanhamento longitudinal da saúde.

**Para clínicas e hospitais:** aumento da recorrência e fidelização de clientes, redução do abandono de tratamentos, dashboard com KPIs em tempo real, e maior LTV por paciente.

**Para a Clyvo Vet:** geração de moat competitivo através de dados, escalabilidade via conteinerização em nuvem, e base para monetização com planos de assinatura e marketplace de serviços.

---

## 🏗️ Arquitetura Macro

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  📱 App Mobile  │     │ 🖥️ Dashboard    │     │ 🔌 IoT/Sensores │
│  (React Native) │     │ (.NET / React)  │     │ (Python/CV)     │
│  Tutor do Pet   │     │ Clínica Vet     │     │ Wearable Pet    │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         └───────────┬───────────┘───────────────────────┘
                     │
                 🌐 Internet
                     │
    ┌────────────────┴─────────────────────────────────────┐
    │  ☁️  Microsoft Azure (North Central US)              │
    │  ┌─────────────────────────────────────────────────┐ │
    │  │  🐧 VM: vm-clyvovet                            │ │
    │  │      Ubuntu 22.04 · Standard_B2als_v2 · 4GB    │ │
    │  │  ┌───────────────────────────────────────────┐  │ │
    │  │  │  🐳 Docker Engine + Compose               │  │ │
    │  │  │      rede: clyvovet-network               │  │ │
    │  │  │                                           │  │ │
    │  │  │  ┌─────────────────────────────────────┐  │  │ │
    │  │  │  │ ☕ api-clyvovet                     │  │  │ │
    │  │  │  │ Spring Boot 4.0.6 · Java 21        │  │  │ │
    │  │  │  │ Porta: 8080                        │  │  │ │
    │  │  │  │ Usuário: appuser (não-root)        │  │  │ │
    │  │  │  └──────────────┬──────────────────────┘  │  │ │
    │  │  │                 │ JDBC :1521               │  │ │
    │  │  │  ┌──────────────┴──────────────────────┐  │  │ │
    │  │  │  │ 🗄️ oracle-clyvovet                 │  │  │ │
    │  │  │  │ Oracle XE 21c (gvenzl/oracle-xe)   │  │  │ │
    │  │  │  │ Porta: 1521 · DB: PETFLOWDB        │  │  │ │
    │  │  │  │ Volume: oracle-data (persistência)  │  │  │ │
    │  │  │  └─────────────────────────────────────┘  │  │ │
    │  │  └───────────────────────────────────────────┘  │ │
    │  │  🔒 NSG: Portas 22 · 8080 · 1521               │ │
    │  └─────────────────────────────────────────────────┘ │
    └──────────────────────────────────────────────────────┘
```

---

## 🚀 Instalação da Solução — Azure VM (How to)

### Pré-requisitos

- Azure CLI instalado e autenticado (`az login`)
- Git Bash ou terminal Unix-like
- Acesso à subscription Azure for Students

### Passo 1 — Provisionar a infraestrutura

```bash
bash scripts/setup-clyvovet.sh
```

Este script cria o Resource Group, a VM Ubuntu 22.04 (Standard_B2als_v2, 4GB RAM), abre as portas 22, 8080 e 1521, instala Docker e ferramentas (Git, nano, curl, wget, htop).

### Passo 2 — Validar a infraestrutura (opcional)

```bash
bash scripts/validate-clyvovet.sh
```

Verifica 7 itens: Resource Group, VM rodando, IP público, portas 8080 e 1521, Docker instalado, ferramentas.

### Passo 3 — Conectar na VM

```bash
ssh azureuser@<IP_PUBLICO>
```

> As credenciais de acesso são exibidas ao final da execução do `setup-clyvovet.sh`.

### Passo 4 — Clonar o repositório na VM

```bash
cd ~
git clone https://github.com/olavoneves/clyvo-vet_api-java.git
cd clyvo-vet_api-java
```

### Passo 5 — Subir os containers

```bash
docker compose up -d --build
```

O Docker Compose sobe dois containers:
- **oracle-clyvovet** — Oracle XE 21c com healthcheck (~2-3 min para inicializar)
- **api-clyvovet** — API Java Spring Boot (inicia somente após o Oracle estar saudável)

As tabelas são criadas automaticamente pelo JPA (`ddl-auto: update`).

### Passo 6 — Verificar containers

```bash
docker compose ps                  # Containers rodando em background
docker exec api-clyvovet whoami    # Deve retornar: appuser (não-root)
docker volume ls                   # Deve aparecer: oracle-data
```

### Passo 7 — Testar a API

```
http://<IP_PUBLICO>:8080/swagger-ui.html
```

### Passo 8 — Remover recursos (obrigatório ao final)

```bash
bash scripts/cleanup-clyvovet.sh
```

Ou diretamente:

```bash
az group delete --name rg-clyvovet-devops --yes --no-wait
```

---

## 🖥️ Scripts DevOps

Os scripts de infraestrutura estão na pasta `scripts/` e as cenas de demonstração em `scenes/`:

| Script | Descrição |
|---|---|
| `scripts/setup-clyvovet.sh` | Provisiona toda a infraestrutura Azure (VM, NSG, Docker, ferramentas) |
| `scripts/validate-clyvovet.sh` | Valida 7 itens da infraestrutura |
| `scripts/cleanup-clyvovet.sh` | Remove todos os recursos Azure com confirmação |
| `scenes/c1.sh` | Cena 1: Executa setup + validate (Git Bash local) |
| `scenes/c2.sh` | Cena 2: SSH → clone → docker compose up (VM) |
| `scenes/c3.sh` | Cena 3: Valida containers, appuser, volume (VM) |
| `scenes/c4.sh` | Cena 4: CRUD completo via curl com IP público (VM) |
| `scenes/c5.sh` | Cena 5: Consulta Oracle → cleanup (VM → local) |

### Configuração Azure CLI

| Variável | Valor |
|---|---|
| RESOURCE_GROUP | rg-clyvovet-devops |
| LOCATION | northcentralus |
| VM_NAME | vm-clyvovet |
| VM_SIZE | Standard_B2als_v2 (4GB RAM) |
| IMAGE | Ubuntu 22.04 |
| Portas abertas | 22 (SSH), 8080 (Java), 1521 (Oracle) |

---

## 👥 Equipe

| Nome | RM |
|---|---|
| Altamir Lima | 562906 |
| Felipe Conte | 562248 |
| Luiz Gonçalves | 564495 |
| Olavo Neves | 563558 |
| Pedro França | 561940 |

**Equipe:** PetFlow  
**Curso:** Tecnologia em Desenvolvimento de Sistemas — FIAP  
**Challenge:** Clyvo Vet — 1º e 2º Sprint (2025/2026)