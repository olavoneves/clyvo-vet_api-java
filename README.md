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
- [Lembrete Automático](#-lembrete-automático)
- [Agente de Agendamento](#-agente-de-agendamento)
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
- Agente de agendamento conversacional, que fecha o ciclo do lembrete até a receita
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
├── agente/         # Agente de agendamento: laço, ferramentas, guardrail clínico
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

Tabelas Oracle com prefixo `TB_CLV_`. O schema é governado pelo **Flyway**, nunca pelo
Hibernate: `spring.jpa.hibernate.ddl-auto=validate` faz a aplicação recusar subir se o
mapeamento divergir do banco.

### Migrations (`src/main/resources/db/migration/`)

| Arquivo | Finalidade |
|---|---|
| `V0__baseline_schema.sql` | 23 tabelas do modelo inicial, **sem** foreign keys |
| `V0_1__foreign_keys.sql` | As FKs, num passo separado — assim a ordem de criação das tabelas não importa |
| `V1__veterinario_auth_e_refresh_token.sql` | Auth do veterinário + `TB_CLV_REFRESH_TOKEN` |
| `V2__colaborador.sql` | `TB_CLV_COLABORADOR` |
| `V3__multi_tenancy_id_clinica.sql` | `id_clinica` em PET, TUTOR e COLABORADOR |
| `V4__clinica_ticket_medio.sql` | Ticket médio da clínica |
| `V5__consulta_valor.sql` | Valor da consulta |
| `V6__raca_perfil.sql` | Perfil da raça |
| `V7__correcoes_motor.sql` | **Motor de protocolo**: catálogo, obrigações, outbox, auditoria, funções, procedures e a view do painel |
| `V8__melhorias_banco.sql` | Gerador de dados de demonstração (`PR_CLV_SEED_*`) — nada aqui é chamado pela aplicação |
| `V9__catalogo_clinico_e_correcoes.sql` | Catálogo clínico (espécies, raças, 26 protocolos) e correções de regra do motor |
| `V10__senha_dos_usuarios_de_demonstracao.sql` | Regrava as senhas do seed em BCrypt válido |
| `V11__vw_clv_painel_coorte.sql` | `VW_CLV_PAINEL_COORTE` — comparação controle × tratado |
| `V12__notificacao_do_tutor.sql` | `TB_CLV_NOTIFICACAO` — a caixa de entrada do tutor |
| `V13__antecedencia_do_lembrete.sql` | `nr_antecedencia_lembrete_dias` no protocolo: quantos dias antes o lembrete sai |

**Banco vazio** (container do docker-compose): a cadeia `V0 → V0.1 → V1 … → V13` roda
inteira e cria tudo, sem intervenção manual.

**Banco da FIAP**: o schema até a V8 já existia, aplicado à mão pelo DBA. O Flyway está
com `baseline-version=8`, então V0–V8 não executam — ele registra o baseline e segue a
partir da **V9**, que é a primeira migration que o repositório de fato aplica.

### ⚠️ Repair de migration que cria objeto PL/SQL exige um segundo passo

`flyway repair` só reescreve o checksum na `flyway_schema_history`. Ele **não reexecuta
nada**. Um `CREATE OR REPLACE PROCEDURE` corrigido no arquivo continua com o corpo
**antigo** dentro do banco, e a divergência é invisível: a aplicação sobe, o Flyway diz
que está tudo aplicado, os testes passam. O erro aparece depois, na regra que a correção
mudou.

**Não é hipotético.** A conferência de 06/09/2026 encontrou três procedures divergentes
no banco da FIAP — `PR_CLV_SEED_BASE`, `PR_CLV_SEED_HEROIS` e `PR_CLV_SEED_POPULACAO` —
todas ainda com o hash de senha placeholder (`$2a$10$seedhashplaceholder00000`) que a V8
já tinha corrigido para um BCrypt válido no repositório. A V10 consertou as **linhas** de
usuário existentes, então o login funcionava; mas as **procedures** continuavam gravando
o placeholder, e o próximo reseed derrubaria o login de novo. Os cinco objetos do motor
(`PR_CLV_GERAR_OBRIGACOES`, `PR_CLV_TRANSITAR_OBRIGACAO`, `FN_CLV_CALCULAR_DATA`,
`FN_CLV_GRUPO_CONTROLE`, `TR_CLV_AUDIT_OBRIGACAO`) estavam idênticos nos dois bancos.

**O procedimento, depois de todo repair que toque num objeto PL/SQL:**

1. Conferir o corpo do que está no banco contra o que está na migration:

   ```sql
   SELECT name, text FROM user_source ORDER BY name, line;
   ```

   No SQL\*Plus, use `SET TAB OFF` antes de comparar — com `TAB ON` (o padrão) a saída
   troca sequências de espaço por tabulação e **todo** objeto parece divergir.
   O `USER_SOURCE` guarda o texto já sem o `CREATE OR REPLACE [EDITIONABLE]`, então tire
   esse prefixo do lado da migration antes de comparar.

2. **Regravar** cada objeto divergente rodando o trecho `CREATE OR REPLACE` da migration
   atual direto no schema.

3. Conferir de novo. Só aí o repair está terminado.

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

Esta lista é exaustiva. Qualquer rota fora dela exige autenticação.

| Rota | Descrição |
|---|---|
| `POST /api/auth/**` | Login, refresh e logout |
| `POST /api/clinicas` | Autocadastro de clínica na plataforma |
| `POST /api/tutores` | Autocadastro de tutor, com `clinicaId` no corpo |
| `GET /swagger-ui/**`, `GET /v3/api-docs/**` | Documentação da API |
| `GET /actuator/health` | Health check do container |
| `GET /login` + estáticos | Tela de login e CSS/JS das páginas |

> **`POST /api/veterinarios` não é público.** Exige `ROLE_COLABORADOR` e a clínica sai do
> token, não do corpo. Aberto, seria bypass completo do multi-tenant: qualquer pessoa
> criaria um veterinário numa clínica alheia, autenticaria com ele e leria todos os dados
> daquela clínica.

Todas as demais rotas exigem `Authorization: Bearer <accessToken>` (API) ou sessão
autenticada (telas).

---

## 🔀 Rotas da API

### Rotas protegidas (requerem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/especies` | Listar espécies (paginado) |
| `GET` | `/api/especies/{id}` | Buscar espécie por ID |
| `POST` | `/api/especies` | Criar espécie |
| `PUT` | `/api/especies/{id}` | Atualizar espécie |
| `DELETE` | `/api/especies/{id}` | Remover espécie |
| `GET` | `/api/racas` | Listar raças |
| `GET` | `/api/pets` | Listar pets |
| `GET` | `/api/pets/{id}/ficha-tecnica` | Ficha técnica completa do pet |
| `GET` | `/api/consultas` | Listar consultas |
| `GET` | `/api/agendamentos` | Listar agendamentos |
| `GET` | `/api/veterinarios` | Listar veterinários |
| `GET` | `/api/tutores` | Listar tutores |
| `GET` | `/api/clinicas` | Listar clínicas |
| `GET` | `/api/vacinas/tipos` | Listar tipos de vacina |
| `GET` | `/api/vacinas/aplicacoes` | Listar aplicações de vacina |
| `GET` | `/api/exames` | Listar exames |
| `GET` | `/api/prescricoes` | Listar prescrições |
| `GET` | `/api/sensores-iot` | Listar sensores IoT |
| `GET` | `/api/leituras-iot` | Listar leituras IoT |
| `GET` | `/api/alertas-iot` | Listar alertas IoT |

### Rotas do tutor

| Método | Rota | Perfil | Descrição |
|---|---|---|---|
| `POST` | `/api/agente/mensagens` | `TUTOR` | Conversa com o agente de agendamento |
| `GET` | `/api/agente/conversas/{idPet}` | `TUTOR` | Histórico da conversa sobre um pet |

A documentação completa está disponível via Swagger em `/swagger-ui.html`.

> As rotas REST vivem sob `/api`. O prefixo não está escrito nos controllers: é
> aplicado no handler mapping (`WebMvcConfig`), para que as telas Thymeleaf possam
> ocupar `/pets`, `/agenda` e `/painel/receita` sem colidir com a API.

### Exemplo de uso via curl

```bash
# 1. Criar clínica (público)
curl -X POST http://<IP>:8080/api/clinicas \
  -H "Content-Type: application/json" \
  -d '{"nome":"CLYVO Vet SP","cnpj":"12345678000199","logradouro":"Av. Paulista 1000","cidade":"Sao Paulo","estado":"SP"}'

# 2. Login como colaborador (obter token)
curl -X POST http://<IP>:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"master@clyvovet.com","senha":"master","tipo":"COLABORADOR"}'

# 3. Criar veterinario -- exige token de COLABORADOR; a clinica vem do token
curl -X POST http://<IP>:8080/api/veterinarios \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <SEU_TOKEN>" \
  -d '{"nome":"Dr. Paulo Costa","crmv":"SP-67890","email":"paulo@clyvovet.com","senha":"<SUA_SENHA>"}'

# 4. Usar token nas rotas protegidas
curl http://<IP>:8080/api/especies \
  -H "Authorization: Bearer <SEU_TOKEN>"
```

---

## ⏰ Lembrete Automático

O elo que o produto afirmava ter e não tinha. A obrigação nascia `PREVISTA` e ficava
parada até alguém abrir a ficha do pet e apertar um botão: **quem perseguia a obrigação
era o colaborador, não o motor**. O que o sistema oferecia era a lista do que a pessoa
deveria ter feito.

`VarreduraDeLembretes` é um `@Scheduled` que varre as obrigações `PREVISTA` cuja janela
de antecedência já abriu e chama `PR_CLV_TRANSITAR_OBRIGACAO` para levá-las a
`NOTIFICADA`. A notificação já pendia da transição, então a caixa de entrada do tutor
funciona sem alteração nenhuma.

### O que a varredura precisa acertar

| Risco | Como está resolvido |
|---|---|
| **Notificar o grupo de controle** | `fl_grupo_controle = 'N'` no `WHERE` da consulta, lendo a **flag persistida** pelo sorteio de `FN_CLV_GRUPO_CONTROLE`. Nada é recalculado em Java: seriam duas fontes da mesma verdade. Notificar o controle destrói o A/B na primeira execução e não tem desfazer |
| **Tenant vazio fora de requisição** | O job itera as clínicas explicitamente, preenche o `TenantContext` a cada volta e limpa no `finally`. ThreadLocal sujo entre iterações vaza uma clínica na outra |
| **Antecedência codificada em Java** | É regra clínica e mora no catálogo: `TB_CLV_PROTOCOLO.nr_antecedencia_lembrete_dias`, com padrão por categoria (cirurgia 15 dias, checkup 14, odonto 10, vacina e exame 7, vermífugo 5, retorno e monitoramento 3) |
| **Notificar duas vezes** | O próprio filtro de estado: a obrigação sai de `PREVISTA` na primeira passada e a segunda não a encontra mais |

> ⚠️ **A idempotência acima vale para instância única.** Duas instâncias varrendo ao
> mesmo tempo leem a mesma lista antes de qualquer uma escrever, e as duas tentam
> transitar as mesmas linhas. A rede de segurança do banco segura o pior caso — o
> `EmissorDeLembretes` recusa lembrete repetido e a V12 tem índice único por obrigação e
> canal —, mas o resultado seriam transições concorrentes disputando a mesma obrigação.
> **A versão multi-instância precisa de lock distribuído** (ShedLock sobre a própria
> tabela do Oracle, ou um `SELECT ... FOR UPDATE SKIP LOCKED` na seleção da fila).

### O botão da ficha do pet

`Antecipar lembrete`, e não mais "Enviar lembrete": o disparo padrão é o job, e o botão
é a exceção manual, para o caso em que a clínica quer avisar antes de a janela abrir. O
nome antigo dizia que sem ele nada saía — era verdade e deixou de ser.

Ele **não aparece** para pet do grupo de controle, e a rota `POST
/obrigacoes/{id}/lembrete` recusa o pedido mesmo assim: esconder o botão não fecha a
rota, e um POST direto notificaria o controle sem deixar rastro de intenção.

### Configuração

```properties
app.lembretes.habilitado=true
app.lembretes.cron=0 0 8 * * *
app.lembretes.fuso=America/Sao_Paulo
app.lembretes.max-por-execucao=200
```

O teto por execução existe para a primeira varredura de uma base com histórico: sem ele,
o backlog inteiro viraria notificação de uma vez.

---

## 🤖 Agente de Agendamento

O terceiro elo da cadeia de valor era o último com saída: atendimento →
obrigação futura → lembrete → **parava aqui**. O tutor recebia o aviso e não
tinha como agir. O agente fecha os elos que faltavam — entende o pedido,
consulta a agenda real, propõe horários e grava o agendamento, que volta para a
obrigação e entra no painel de receita recuperada.

### Como funciona

Sem framework de agente. Um `RestClient` chamando a API de mensagens da
Anthropic, com ferramentas declaradas e laço de execução próprio:

1. Recebe a mensagem do tutor
2. Monta a requisição com o histórico e a lista de ferramentas
3. Se a resposta vem com `stop_reason: "tool_use"`, executa a ferramenta em Java
   e devolve o resultado como `tool_result`
4. Quando a resposta é texto, entrega ao tutor
5. Teto de 6 voltas por turno; ao estourar, escala para o veterinário

Modelo: `claude-sonnet-4-6`. A chave vem de `ANTHROPIC_API_KEY`, nunca do código.

### Ferramentas

Todas recebem a clínica do `TenantContext` — nenhuma expõe `idClinica` ao
modelo — e são métodos Java sobre serviços que já existiam.

| Ferramenta | O que faz |
|---|---|
| `listar_obrigacoes_pendentes` | Obrigações em `PREVISTA`, `NOTIFICADA` ou `RESPONDIDA` |
| `consultar_disponibilidade` | Horários livres, derivados de `TB_CLV_AGENDAMENTO` |
| `criar_agendamento` | Cria com `ds_canal_origem = 'APP'` e chama `PR_CLV_TRANSITAR_OBRIGACAO` |
| `reagendar` | Move um compromisso já marcado |
| `escalar_para_veterinario` | Passa a conversa para uma pessoa |

### Guardrail clínico

O agente **nunca** opina sobre saúde do animal. Sem diagnóstico, sem dosagem,
sem interpretação de sintoma, sem recomendação de tratamento — mesmo que o tutor
insista, mesmo que a informação esteja no prontuário.

Duas camadas, e a segunda é a que importa na defesa:

1. **Prompt do sistema** — instrução explícita de escalar qualquer questão clínica.
2. **Verificação em Java sobre a resposta final** (`GuardrailClinico`) — padrões de
   conteúdo clínico. Se disparar, a resposta é substituída pela mensagem de
   escalonamento, o evento é registrado, e o texto barrado sai também do
   histórico, para não voltar à API no turno seguinte.

A segunda camada existe porque instrução em prompt não é controle. A resposta a
"por que confiar no agente?" é que não confiamos — verificamos na saída.

### Estado da conversa

Caffeine com validade de 24h, chaveado por clínica, tutor e pet. Cada turno é
gravado em `TB_CLV_OUTBOX_EVENT` como evento `ConversaAgente`, para auditoria e
para o clyvo-insights consumir depois — a memória é de trabalho, o registro é o
outbox.

> O spec previa Redis quando configurado. Não foi adotado: subir Redis contraria
> a decisão que o projeto já tinha tomado em `CacheConfig` — a demonstração não
> pode depender de um serviço externo de pé. O custo é conhecido e aceito: com
> mais de uma instância, o tutor que cair em outra perde o fio da conversa (o
> histórico, esse, não se perde).

### Resiliência

- Timeout de 30s; ao estourar, mensagem de indisponibilidade e escalonamento
- Retry com backoff apenas em `429` e `5xx`, no máximo 2 tentativas extras
- **Sem `ANTHROPIC_API_KEY` a aplicação sobe normalmente** e apenas
  `/api/agente/**` responde `503`. Nada mais no sistema depende disso.
- Concorrência: `criar_agendamento` trava a linha do veterinário
  (`SELECT ... FOR UPDATE`) e revalida a disponibilidade dentro da transação; o
  conflito volta ao modelo como resultado de ferramenta, e ele propõe outro horário

### Superfície do tutor

`/tutor/pets/{id}` — servida pela cadeia de `formLogin`, com login de tutor
habilitado. Mostra dados do pet, obrigações pendentes, carteirinha de vacinas e o
campo de conversa. Quando o agente confirma um agendamento, a lista de pendências
é recarregada.

Não é o app do tutor: é a tela que fecha o laço da demonstração.

### Roteiro da demonstração

Veterinário registra consulta no painel → a obrigação futura aparece, gerada pelo
motor → tutor abre `/tutor/pets/{id}`, vê a pendência → escreve pedindo horário →
o agente propõe, o tutor escolhe, confirma → o agendamento aparece na agenda do
veterinário → o painel de receita recuperada sobe.

### Testes

A suíte usa um stub HTTP da API (`MockRestServiceServer`) — nenhuma chamada real,
nenhum crédito gasto. O que fica sob teste é o que é nosso: o laço, o despacho de
ferramenta, o tratamento de conflito, o guardrail de saída e o isolamento por
tutor. A qualidade da escolha do modelo não é testável com stub, e o Javadoc de
`AgenteServiceTest` diz isso explicitamente.

```bash
./mvnw test -Dtest='Agente*,GuardrailClinicoTest'
```

---

## 💻 Rodando Localmente

### Com Docker Compose (recomendado)

```bash
docker compose up --build
```

Aguarde o Oracle ficar healthy (~2 min). O banco sobe **vazio**: o Flyway executa a cadeia
`V0 → V0.1 → V1 … → V10` e cria o schema inteiro — tabelas, foreign keys, índices, o motor
de protocolo em PL/SQL e a view do painel. Nenhum passo manual.

Para popular com dados de demonstração depois que a API subir:

```sql
BEGIN PR_CLV_SEED_EXECUTAR(p_qtd_pets => 400); END;
/
```

### Logins de demonstração

Ninguém "cria" esses usuários: eles nascem do seed, todos com a senha
**`Clyvo@2026`**. Não são credenciais de produção.

| Perfil | E-mail | Clínica |
|---|---|---|
| Colaborador | `patricia@vidaanimal.com.br` | Vida Animal |
| Colaborador | `diego@petcare.com.br` | PetCare |
| Veterinário | `helena@vidaanimal.com.br` | Vida Animal |
| Tutor (Thor) | `camila.ferreira@exemplo.com` | Vida Animal |
| Tutor (Nala) | `roberto.almeida@exemplo.com` | Vida Animal |

O fluxo completo atravessa duas telas e dois logins — use uma janela anônima
para a segunda sessão, senão uma derruba a outra. Entre como Patricia ou
Helena, registre uma consulta para o Thor e veja a obrigação nascer; entre como
Camila em `/tutor/pets/{id}`, converse com o agente e agende; volte para a
Helena e o agendamento está na agenda.

Base semeada antes de 2026-09-04 carrega o hash quebrado da V8 antiga — a
**V10** corrige, basta subir a aplicação.

API: `http://localhost:8080`  
Swagger: `http://localhost:8080/swagger-ui.html`

### Sem Docker (banco FIAP)

1. Copie `.env.example` para `.env` e preencha
2. Nada de migration manual: o Flyway está com `baseline-version=8` e reconhece o schema
   existente sem executar nada
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
| `JWT_SECRET` | Secret HS256 (mín. 32 chars) | — (obrigatória) |
| `CORS_ALLOWED_ORIGINS` | Origens autorizadas a chamar `/api` de um navegador, separadas por vírgula | vazio (nenhuma) |
| `ANTHROPIC_API_KEY` | Chave da API da Anthropic, usada pelo agente de agendamento | vazio (agente desligado) |
| `PORT` | Porta HTTP | `8080` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | DDL auto | `none` |

> ⚠️ **Nunca commite credenciais reais.** Use variáveis de ambiente ou arquivo `.env` (que está no `.gitignore`).
> Há um `.env.example` versionado com o formato esperado — copie para `.env` e preencha.

> 📌 A variável do JWT chamava-se `APP_JWT_SECRET` até a fase de hardening. Se você tem um
> `.env` ou um ambiente de deploy antigo, **renomeie para `JWT_SECRET`** — a aplicação não
> sobe sem ela.

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