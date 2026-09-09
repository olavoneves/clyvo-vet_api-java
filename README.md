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
- [Painel — Receita Recuperada e Coorte](#-painel--receita-recuperada-e-coorte)
- [Lembrete Automático](#-lembrete-automático)
- [Agente de Agendamento](#-agente-de-agendamento)
- [Rodando Localmente](#-rodando-localmente)
- [Variáveis de Ambiente](#-variáveis-de-ambiente)
- [Mapa dos Requisitos](#-mapa-dos-requisitos)
- [Decisões de Arquitetura](#-decisões-de-arquitetura)
- [Uso de IA no Desenvolvimento](#-uso-de-ia-no-desenvolvimento)
- [Deploy — Render](#-deploy--render)
- [Git Flow](#-git-flow)
- [Screenshots](#-screenshots)
- [Benefícios para o Negócio](#-benefícios-para-o-negócio)
- [Arquitetura da Solução](#-arquitetura-da-solução)
- [Deploy na Azure — ACR + ACI (How to)](#-deploy-na-azure--acr--aci-how-to)
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
- Autenticação JWT (access token 15min + refresh token 7 dias) e multi-tenancy por clínica
- **Motor de protocolo em PL/SQL**: registrar uma consulta materializa as obrigações
  futuras de cuidado, com máquina de estados, auditoria encadeada e outbox
- **Lembrete automático**: um job diário persegue a obrigação e leva o aviso ao tutor
- **Agente de agendamento conversacional**, que fecha o ciclo do lembrete até a consulta
- **Painel de receita recuperada**, com grupo de controle para separar o que o produto
  causou do que teria acontecido de qualquer jeito
- Documentação interativa via Swagger

---

## 🛠️ Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Banco de Dados | Oracle XE 21c (Docker) / Oracle FIAP |
| Migrations | Flyway (core + `flyway-database-oracle`) |
| Telas | Thymeleaf + `thymeleaf-extras-springsecurity6`, CSS próprio, Chart.js 4.4.7 |
| Autenticação | JWT manual — JJWT 0.12.6 (HS256) + `formLogin` para as telas |
| Criptografia | BCrypt via spring-security-crypto |
| Cache | Caffeine (só o catálogo de protocolos) |
| Rate limit | Bucket4j 8.7.0, em memória, por IP |
| LLM do agente | Porta `ProvedorLlm` — Gemini (ativo) ou Anthropic, por propriedade |
| Documentação | SpringDoc OpenAPI 2.8.9 (Swagger UI) |
| Build | Maven 3.9 (wrapper `mvnw`) |
| Containerização | Docker multi-stage + Docker Compose |
| Infraestrutura | Microsoft Azure, Azure CLI, Ubuntu 22.04 |

---

## 📦 Estrutura de Pacotes

```
br.com.clyvovet.server
├── agente/         # Agente de agendamento: laço, ferramentas, guardrail clínico
│   └── llm/        # Porta ProvedorLlm + adaptadores Gemini e Anthropic
├── obrigacao/      # Motor de protocolo: obrigações, transições, web/
├── protocolo/      # Catálogo clínico: protocolo, versão, etapa, regra de ativação
├── notificacao/    # Caixa do tutor, emissor e a varredura agendada de lembretes
├── painel/         # Receita recuperada e coorte tratado × controle
├── outbox/         # OutboxEvent — fila de integração escrita pelo motor
├── tenant/         # TenantContext, TenantFilter e as condições do @Filter
├── ratelimit/      # Bucket4j por IP, com teto menor nas rotas de auth
├── auth/           # Login, RefreshToken, JwtService, AuthService, web/
├── config/         # SecurityConfig, WebMvcConfig, CacheConfig, OpenApiConfig, DataInitializer
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

### Telas (Thymeleaf, fora do `/api`)

| Rota | Perfil | Tela |
|---|---|---|
| `/painel/receita` | equipe | Funil do mês, receita recuperada e coorte tratado × controle |
| `/agenda` | equipe | Compromissos por data do compromisso, com a obrigação atrás |
| `/pets`, `/pets/{id}` | equipe | Lista e ficha, com as obrigações do protocolo |
| `POST /obrigacoes/{id}/lembrete` | equipe | Antecipa o lembrete de uma obrigação |
| `/tutor/pets/{id}` | tutor | Ficha do pet e a conversa com o agente |
| `/tutor/caixa` | tutor | Caixa de entrada dos lembretes |

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

## 📊 Painel — Receita Recuperada e Coorte

O painel responde a pergunta que justifica o produto: **quanto da receita aconteceu
porque o Clyvo cobrou, e quanto teria acontecido de qualquer jeito.**

Para poder responder isso, `FN_CLV_GRUPO_CONTROLE` sorteia ~10% dos pets para um **grupo
de controle** que nunca recebe lembrete e nunca entra no agente. A diferença entre a taxa
de cumprimento dos dois grupos é o efeito do produto; sem o controle, o número do painel
seria só a taxa de comparecimento da clínica, que existiria com ou sem software.

O sorteio é **persistido** em `TB_CLV_OBRIGACAO.fl_grupo_controle`, com o seed e o hash
que o produziram. Toda leitura usa a flag gravada — nunca recalcula em Java, nunca chama
a função de novo. Duas fontes da mesma verdade divergiriam no dia em que os parâmetros do
sorteio mudassem, e aí ninguém saberia qual delas o número da tela usou.

### ⚠️ O que é real e o que é simulado

Este é um projeto acadêmico e não tem 18 meses de clínica de verdade atrás dele. A
distinção importa, e é melhor deixá-la escrita do que deixá-la subentendida:

| | Situação |
|---|---|
| Sorteio do grupo de controle | **Real.** `FN_CLV_GRUPO_CONTROLE` usa `SHA256` sobre um seed determinístico por pet, persiste flag e hash, e é auditável depois do fato |
| Separação de caminho no motor | **Real.** A varredura de lembretes exclui o controle no `WHERE`, o botão da ficha não aparece para ele e a rota recusa o POST. Um pet de controle não recebe lembrete nem entra no agente |
| Agregação e aritmética do painel | **Reais.** `VW_CLV_PAINEL_COORTE` conta as linhas que existem, e o delta, as consultas atribuíveis e o valor saem dessa contagem |
| **Os desfechos dos 18 meses** | **Simulados.** `PR_CLV_SEED_DESFECHOS` sorteia quem cumpriu e quem perdeu com taxas **declaradas nos parâmetros da própria procedure** — hoje `p_taxa_tratado => 0.58` e `p_taxa_controle => 0.36` |

Ou seja: **o mecanismo é real, o histórico é sintético.** Os +25 p.p. que o painel mostra
não são uma descoberta — são, aproximadamente, a diferença que o seed foi instruído a
produzir, e é por isso que o teste de integração confere a taxa da view contra o parâmetro
da procedure em vez de contra um número escrito no teste.

O que o painel demonstra de verdade é que **a instrumentação está de pé**: se estas
clínicas fossem reais, o número apareceria por este caminho, medido deste jeito, com o
controle protegido por estas guardas. É o encanamento que está pronto para receber dados
de verdade — não a evidência clínica.

### O denominador são as obrigações resolvidas

`VW_CLV_PAINEL_COORTE` conta apenas `CUMPRIDA` e `PERDIDA`. Uma obrigação que ainda está
`PREVISTA` não é um não comparecimento — ela nem teve a chance. Contá-la afundava as duas
taxas e, pior, fazia o número andar sozinho com o calendário: a mesma coorte "piorava" a
cada mês que passava sem que nada acontecesse. Era isso que mostrava 37% onde o seed
sorteia 58%. `CANCELADA` fica fora: cancelamento é decisão da clínica, não desfecho do
tutor.

### O universo de cada número do card

Todos os números do card saem de `VW_CLV_PAINEL_COORTE`, e **o universo dela são as
obrigações já resolvidas** — `CUMPRIDA` ou `PERDIDA`. Nenhum número ali fala sobre a
clínica inteira, e a distinção importa mais do que parece:

| Número no card | Numerador | Denominador |
|---|---|---|
| Taxa de cumprimento (tratado / controle) | Obrigações `CUMPRIDA` do grupo | Obrigações **resolvidas** do grupo |
| Delta em p.p. | — | Diferença entre as duas taxas acima |
| Consultas atribuíveis | Obrigações resolvidas do tratado × delta | — |
| Valor atribuível | Consultas atribuíveis × ticket médio | — |
| Ticket médio | Soma de `nr_valor` das consultas `REALIZADA` | Quantidade dessas consultas |
| **Fatia do controle (rodapé)** | Pets do controle **com obrigação resolvida** | Pets dos dois grupos **com obrigação resolvida** |

A última linha é a que já causou confusão. O rodapé dizia *"X% dos pets desta
clínica"*, mas o denominador nunca foi o total de pets da clínica — é o mesmo
denominador do card. Os dois são próximos e não iguais: na base local, 245 pets na
coorte contra 250 pets cadastrados; na FIAP, 238 contra 241. A diferença são os pets
que ainda não têm nenhuma obrigação fechada. O texto passou a nomear o universo que de
fato usa (*"dos pets com obrigação já resolvida"*), porque número certo com rótulo
errado continua sendo número errado.

> **Números lidos em bancos diferentes não se comparam.** O seed usa `SYS_GUID` e datas
> relativas a `SYSDATE`, então o Docker local e a FIAP têm populações diferentes e
> sorteios diferentes — em 06/09/2026, 27 pets de controle no local e 28 na FIAP.
> As **taxas** são reprodutíveis entre bancos; as **contagens** não. Ao conferir um
> número do card, rode as duas consultas no mesmo banco.

### Tamanho da amostra — medição de 06/09/2026

Como o denominador mudou, o `n` do controle mudou junto. Os números abaixo são do banco
da FIAP, que é o que a demonstração usa:

| Clínica | Grupo | Resolvidas | Cumpridas | Taxa | Pets |
|---|---|---:|---:|---:|---:|
| Clínica Vida Animal | Controle | 312 | 105 | **33,65%** | 28 |
| Clínica Vida Animal | Tratado | 2.449 | 1.449 | **59,17%** | 210 |
| PetCare Zona Sul | Controle | 178 | 56 | **31,46%** | 11 |
| PetCare Zona Sul | Tratado | 1.767 | 1.042 | **58,97%** | 146 |

Delta de **+25,5 pontos** na Vida Animal e **+27,5** na PetCare. As taxas do grupo tratado
(59,2% e 59,0%) batem com os 0,58 que `PR_CLV_SEED_DESFECHOS` sorteia, o que é a
confirmação de que o denominador agora mede o que diz medir.

### O piso de amostra conta obrigações, não pets

Para exibir o valor em reais, o card exige do grupo de controle:

| Critério | Constante | Mínimo |
|---|---|---|
| **Principal** — obrigações resolvidas | `CoorteResponse.MINIMO_DE_OBRIGACOES_NO_CONTROLE` | **50** |
| Secundário — pets distintos | `CoorteResponse.MINIMO_DE_PETS_NO_CONTROLE` | 5 |

O piso era de **10 pets**, e media a coisa errada. O que sustenta a comparação é a
quantidade de desfechos observados, não a de animais: as 178 obrigações resolvidas da
PetCare são amostra confortável mesmo vindo de 11 pets, e o critério antigo as escondia
por um número que não falava sobre elas.

Pior, era frágil de um jeito invisível. O sorteio é determinístico por pet, mas os pets
mudam a cada seed — a PetCare tinha 11 contra um piso de 10, e uma base nova podia cair
em 8. O número principal do painel sumiria da tela sem que nada tivesse piorado, e sem
nenhum aviso de que aquilo era o piso agindo.

**Cinquenta** porque é onde um desfecho individual para de mover a taxa em ponto inteiro:
com 50 resolvidas, um caso vale 2 p.p.; com 10, vale 10 p.p. — a mesma ordem de grandeza
do efeito que se quer medir.

O critério de pets continua existindo, mais baixo, contra a amostra **concentrada**:
cinquenta obrigações de dois pets não são cinquenta observações independentes, porque as
obrigações de um mesmo animal sobem e descem juntas com o comportamento de um único
tutor. Com o novo piso, as duas clínicas passam com folga (312 e 178 resolvidas).

O banco local do docker-compose tem outra amostra (controle com 27 e 18 pets) e chega às
mesmas taxas — tratado 59,0% e 58,4%, controle 33,9% e 33,7%. As **taxas** são
reprodutíveis; as **contagens** não, porque o seed sorteia quantidades a cada execução.

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

Sem framework de agente. Um `RestClient` chamando a API do provedor de LLM, com
ferramentas declaradas e laço de execução próprio:

1. Recebe a mensagem do tutor
2. Monta a requisição com o histórico e a lista de ferramentas
3. Se a resposta vem com `stop_reason: "tool_use"`, executa a ferramenta em Java
   e devolve o resultado como `tool_result`
4. Quando a resposta é texto, entrega ao tutor
5. Teto de 6 voltas por turno; ao estourar, escala para o veterinário

### Qual LLM atende

O módulo não sabe quem está do outro lado. Há uma porta — `ProvedorLlm` — e dois
adaptadores; `app.agente.provedor` escolhe qual bean sobe, e é a **única** mudança
necessária para trocar de fornecedor.

| | Ativo | Alternativo |
|---|---|---|
| Provedor | **Gemini** | Anthropic |
| Modelo | `gemini-3.1-flash-lite` | `claude-sonnet-4-6` |
| Chave | `GEMINI_API_KEY` | `ANTHROPIC_API_KEY` |

**Gemini por custo**, e não por qualidade: o tier gratuito cobre a demonstração
inteira e este projeto não pode ter custo. A escolha do modelo dentro do Gemini
também é de cota — no tier gratuito o limite é por modelo e por dia, e nos `flash`
ele é de **20 requisições/dia**, que uma única conversa consome (cada volta do laço
é uma requisição). Os `flash-lite` têm cota folgada, e são a única família que
sustenta uma demonstração inteira.

Duas armadilhas do Gemini que custaram uma sessão cada, registradas no código:

- **Listar o modelo não prova que ele atende.** `gemini-2.5-flash` aparece em
  `/v1beta/models` e o `generateContent` o recusa para chaves novas
  (*"no longer available to new users"*).
- **O `thoughtSignature` da chamada de ferramenta tem que voltar.** Sem devolvê-lo
  junto do `functionResponse`, a segunda volta do laço leva `400` — e só a segunda,
  então o erro não aparece em nenhuma conversa de uma pergunta só.

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

- Timeout de **60s**; ao estourar, mensagem de indisponibilidade e escalonamento.
  Não são 30s porque o teto tem que ser maior que a pior resposta aceitável, e não
  igual a ela: com 30s o modelo estourava em parte das voltas e o tutor via a
  mensagem de indisponibilidade no meio de uma conversa que ia bem
- Retry com backoff apenas em `429` e `5xx`, no máximo 2 tentativas extras;
  timeout escala direto, sem retentar
- **Falha do provedor nunca vira `500`.** Timeout na leitura chega como
  `RestClientException` e não como `ResourceAccessException` — escapava do `catch`
  e vazava stack trace. Hoje qualquer falha do provedor sai como indisponibilidade
- **Sem a chave do provedor ativo a aplicação sobe normalmente** e apenas
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

A cadeia inteira, sem passo manual no meio:

1. Veterinário registra a consulta → o **motor de protocolo** materializa as
   obrigações futuras de cuidado daquele pet
2. A **varredura diária** encontra a obrigação cuja janela de antecedência abriu e
   a leva a `NOTIFICADA` — e a transição faz nascer o lembrete
3. O tutor abre `/tutor/caixa`, vê o lembrete, e daí vai para `/tutor/pets/{id}`
4. Escreve pedindo horário → o **agente** consulta a agenda real, propõe, o tutor
   escolhe e confirma
5. O agendamento aparece na **agenda do veterinário**, com a obrigação atrás
6. O **painel de receita recuperada** sobe, e o card de coorte mostra quanto disso
   o produto causou

> Para apresentar sem esperar o cron das 8h, use o botão **Antecipar lembrete** na
> ficha do pet — ele faz exatamente a mesma transição que o job faria. Ele não
> aparece para pet do grupo de controle, o que também é parte do que há para
> mostrar.

### Testes

A suíte usa um stub HTTP da API (`MockRestServiceServer`) — nenhuma chamada real,
nenhum crédito gasto. O que fica sob teste é o que é nosso: o laço, o despacho de
ferramenta, o tratamento de conflito, o guardrail de saída e o isolamento por
tutor. A qualidade da escolha do modelo não é testável com stub, e o Javadoc de
`AgenteServiceTest` diz isso explicitamente.

```bash
./mvnw test -Dtest='Agente*,GuardrailClinicoTest,PortabilidadeDoProvedorTest'
```

`PortabilidadeDoProvedorTest` roda o mesmo diálogo contra os dois adaptadores: é o
que impede a porta de vazar o formato de um fornecedor para dentro do laço.

---

## 🗺️ Mapa dos Requisitos

Onde cada item avaliado está no código.

### 1. Camada de visualização

Thymeleaf, templates em `src/main/resources/templates/`, folha de estilo única em
`src/main/resources/static/css/app.css`. Não há framework de CSS: o conjunto de
componentes é pequeno e cabe inteiro numa folha.

| Tela | Template | Controller |
|---|---|---|
| Login (equipe e tutor) | `login.html` | `auth/web/LoginWebController` |
| Painel de receita e coorte | `painel/receita.html` | `painel/web/PainelWebController` |
| Agenda da clínica | `agenda/lista.html` | `obrigacao/web/AgendaWebController` |
| Lista de pets | `pets/lista.html` | `pet/web/PetWebController` |
| Ficha do pet | `pets/detalhe.html` | `pet/web/PetWebController` |
| Antecipar lembrete | — (só POST) | `obrigacao/web/LembreteWebController` |
| Caixa de lembretes do tutor | `tutor/caixa.html` | `notificacao/web/NotificacaoWebController` |
| Pet e conversa do tutor | `tutor/pet.html` | `tutor/web/TutorWebController` |
| Layout e cabeçalhos | `layout.html`, `tutor/cabecalho.html` | — |
| Erro | `error.html` | — |

Os gráficos do painel são Chart.js alimentados pelo modelo — série, rótulos e escala saem
de consulta, nunca de array literal (`FunilView`, `ComparacaoView`).

### 2. Flyway

`src/main/resources/db/migration/`, **V0 a V13**. A tabela completa está em
[Banco de Dados](#-banco-de-dados); em faixas:

| Faixa | O que faz |
|---|---|
| `V0` – `V0.1` | Schema base: 23 tabelas e, em passo separado, as foreign keys |
| `V1` – `V6` | Autenticação, colaborador, multi-tenancy (`id_clinica`) e campos de negócio |
| `V7` | **Motor de protocolo**: catálogo, obrigações, outbox, auditoria encadeada, funções, procedures e a view do painel |
| `V8` | Gerador de dados de demonstração (`PR_CLV_SEED_*`) — nada aqui é chamado pela aplicação |
| `V9` | Catálogo clínico: espécies, raças e 26 protocolos, com correções de regra do motor |
| `V10` – `V13` | Senhas do seed em BCrypt válido, view da coorte, caixa do tutor e a antecedência do lembrete |

A observação sobre **repair de objeto PL/SQL exigir regravação do objeto como segundo
passo** está em [Banco de Dados](#-banco-de-dados), com o procedimento e a armadilha do
`SET TAB OFF`.

### 3. Spring Security — dois perfis, rotas separadas

`TipoUsuario` tem três valores: **`COLABORADOR`**, **`VETERINARIO`** e **`TUTOR`**. Os
dois primeiros são a equipe da clínica; o terceiro é o dono do pet. A proteção por perfil
está declarada em **`config/SecurityConfig`**, em duas `SecurityFilterChain` separadas —
a da API, por token JWT, e a das telas, por sessão de formulário.

| Perfil | Alcança nas telas | Alcança na API |
|---|---|---|
| `COLABORADOR` | `/`, `/painel/**`, `/pets/**`, `/agenda/**`, `/obrigacoes/**` | Prontuário e gestão (`/api/consultas/**`, `/api/anamneses/**`, `/api/prescricoes/**`, `/api/exames/**`, `/api/vacinas/**`, `/api/alergias-pet/**`, `/api/condicoes-pet/**`, `/api/obrigacoes/**`, `/api/painel/**`) e, **só ele**, `POST /api/veterinarios` |
| `VETERINARIO` | as mesmas telas de gestão | as mesmas rotas de prontuário e gestão |
| `TUTOR` | `/tutor`, `/tutor/**` — e **nada** das telas da clínica | `/api/agente/**` (o agente de agendamento) |
| anônimo | `/login`, `/error`, CSS, Swagger, `/actuator/health` | `/api/auth/**`, `POST /api/clinicas`, `POST /api/tutores` |

Três coisas que a tabela não mostra e importam:

- **`anyRequest().authenticated()`** fecha as duas cadeias: rota nova nasce protegida, e
  não aberta por esquecimento.
- **Multi-tenancy é ortogonal ao perfil.** Ter o papel certo não basta: o
  `TenantContext`, alimentado pelo JWT ou pela sessão, entra num `@Filter` do Hibernate
  ligado por `autoEnabled` que recorta **toda** consulta pela clínica. Um colaborador da
  clínica A não enxerga o pet da B nem sabendo o id — `applyToLoadByKey` estende o filtro
  ao `findById`. Coberto por `IsolamentoMultiTenantIntegracaoTest`.
- **Rate limit por IP** (`ratelimit/RateLimitFilter`, Bucket4j) com teto muito menor nas
  rotas de autenticação: 5 por minuto contra 100.

Testes: `VeterinarioControllerSecurityTest` e `AgenteControllerSecurityTest` afirmam que
o perfil errado leva 403 e que o anônimo leva 401.

### 4. Funcionalidades completas (não-CRUD)

**Fluxo 1 — Do atendimento à obrigação cobrada.** A clínica registra a consulta; o motor
de protocolo em PL/SQL materializa as obrigações futuras de cuidado daquele pet segundo o
catálogo clínico; a varredura diária encontra as que entraram na janela de antecedência e
as leva a `NOTIFICADA`; a transição faz nascer o lembrete na caixa do tutor.

> Telas: `/pets/{id}` (a ficha, onde as obrigações aparecem e onde fica o botão
> **Antecipar lembrete**) → `/tutor/caixa` (o lembrete chegando).
> Código: `obrigacao/MotorProtocolo`, `notificacao/VarreduraDeLembretes`,
> `notificacao/EmissorDeLembretes`.

**Fluxo 2 — Do lembrete à consulta marcada.** O tutor abre o lembrete, conversa em
linguagem natural com o agente de agendamento; o agente consulta a disponibilidade real,
propõe horários, o tutor escolhe, e o agendamento é gravado — o que devolve a obrigação
ao trilho e faz o compromisso aparecer na agenda do veterinário.

> Telas: `/tutor/caixa` → `/tutor/pets/{id}` (a conversa) → `/agenda` (o compromisso na
> agenda da clínica).
> Código: `agente/AgenteService`, `agente/ferramentas/*`, `agendamento/AgendaService`.

Nenhum dos dois é cadastro: o primeiro é uma máquina de estados com disparo automático, o
segundo é um diálogo com ferramentas e escrita transacional. O painel de receita
recuperada mede o efeito dos dois.

**Validações.**

| Onde | Como |
|---|---|
| Formulário / corpo da requisição | Bean Validation nos `*Request` (`@NotBlank`, `@NotNull`, `@Email`, `@Size`, `@Positive`), acionada por `@Valid` nos controllers |
| Resposta de erro | `exception/GlobalExceptionHandler` traduz violação em `400` com os campos, sem vazar stack trace |
| Regra de negócio na aplicação | `AgendaService` recusa fim de semana, fora do expediente e horário não múltiplo de 30 min |
| Regra clínica | No banco: `PR_CLV_TRANSITAR_OBRIGACAO` recusa salto ilegal de estado com `ORA-20010`, traduzido em `409` por `MotorProtocolo` |
| Guardrail do agente | `agente/GuardrailClinico` barra dosagem, diagnóstico e prescrição na saída do modelo |
| Integridade | Constraints no schema: `CHECK` de estado, `UNIQUE` de CNPJ, e-mail, CRMV e microchip |

---

## 🏛️ Decisões de Arquitetura

**Package-by-feature, e não por camada.** `obrigacao/` tem entidade, repositório,
serviço, controller e DTOs juntos. Uma mudança de funcionalidade toca um diretório em vez
de cinco, e o que é interno pode continuar sendo — pacote por camada obriga tudo a ser
público para o pacote de cima enxergar.

**PL/SQL é o motor; Java não reimplementa regra clínica.** Quando gerar obrigação, qual
transição de estado é legal, como calcular a data prevista e quem cai no grupo de
controle vivem no banco, junto com a auditoria encadeada e o outbox, na mesma transação.
`MotorProtocolo` é o único ponto da aplicação que fala com as procedures, e só traduz
`ORA-20010` em erro de domínio. Reimplementar a regra em Java criaria duas fontes de
verdade que divergiriam na primeira regra nova.

**Hexagonal onde há troca real, e não em toda parte.** Só duas fronteiras têm porta e
adaptador: `ProvedorLlm`, com Gemini e Anthropic atrás dela, e `MotorProtocolo`, sobre as
procedures. Nos dois casos existe um fornecedor externo que pode mudar. O resto do
sistema é Spring MVC direto — abstrair o que não vai trocar é custo sem contrapartida.

**Multi-tenancy por `TenantContext` + `@Filter`, e não por `where` em cada consulta.** O
filtro é `autoEnabled`: nasce ligado em toda sessão do Hibernate, então um repositório
novo já vem recortado sem que ninguém precise lembrar. O preço é que o isolamento não
está escrito em nenhuma consulta e por isso precisa de teste de integração próprio, que
existe.

**Thymeleaf server-side, e não SPA.** As telas existem para mostrar a cadeia de valor
funcionando; um front separado dobraria a superfície sem acrescentar nada ao que está
sendo avaliado.

---

## 🤖 Uso de IA no Desenvolvimento

Duas coisas diferentes, que vale separar.

**IA no produto.** O agente de agendamento conversa com o tutor usando um LLM externo,
hoje o **Gemini** (`gemini-3.1-flash-lite`), atrás da porta `ProvedorLlm`. Não há
framework de agente: o laço de execução, o catálogo de ferramentas, a memória da conversa
e o guardrail clínico são código nosso — o modelo escolhe qual ferramenta chamar, e a
ferramenta é um método Java sobre serviços que já existiam. O guardrail roda **fora** do
adaptador, sobre a resposta já traduzida, para que trocar de fornecedor não possa
desligá-lo por esquecimento.

**IA no desenvolvimento.** O projeto foi desenvolvido com apoio do **Claude Code**
(Anthropic) como par de programação — os commits registram isso em `Co-Authored-By`. O
uso foi de escrita e refatoração assistidas: descrição do problema e das restrições,
código proposto, revisão e decisão minhas. As decisões de arquitetura desta seção, o
recorte do domínio e o desenho do experimento de coorte foram definidos por mim e
mantidos ao longo das sessões.

Onde a assistência mais rendeu foi em varredura e conferência — comparar o PL/SQL do
banco contra as migrations, achar testes que se puliam em silêncio, rastrear número de
tela que não vinha de consulta. Onde ela menos rendeu foi em regra clínica e em decisão
de produto, que exigem contexto que não está no repositório.

---

## 💻 Rodando Localmente

### Pré-requisitos

| Ferramenta | Versão | Necessária para |
|---|---|---|
| **Docker Desktop** | 24+ (com Compose v2) | Caminho recomendado — sobe Oracle e API juntos |
| **JDK** | **21** | Compilar e rodar sem Docker; o `pom.xml` fixa `<java.version>21` |
| **Maven** | não precisa instalar | O wrapper `mvnw` / `mvnw.cmd` baixa a versão certa |
| **Oracle** | XE 21c ou o da FIAP | O container já traz o XE; sem Docker, aponte para o seu |

Não é preciso instalar Flyway: ele roda dentro da aplicação, no arranque.

### Com Docker Compose (recomendado)

```bash
git clone <url-do-repositorio>
cd server
cp .env.example .env      # preencha GEMINI_API_KEY se quiser o agente ligado
docker compose up --build
```

Aguarde o Oracle ficar healthy (~2 min). O banco sobe **vazio**: o Flyway executa a cadeia
`V0 → V0.1 → V1 … → V13` e cria o schema inteiro — tabelas, foreign keys, índices, o motor
de protocolo em PL/SQL e a view do painel. Nenhum passo manual.

Para popular com dados de demonstração depois que a API subir:

```sql
BEGIN PR_CLV_SEED_EXECUTAR(p_qtd_pets => 400); END;
/
```

### Logins de demonstração

Ninguém "cria" esses usuários: eles nascem do seed, todos com a senha
**`Clyvo@2026`**. Não são credenciais de produção.

| Perfil | E-mail | Clínica | Cai em | Enxerga |
|---|---|---|---|---|
| Colaborador | `patricia@vidaanimal.com.br` | Vida Animal | `/painel/receita` | Painel, agenda, pets da **sua** clínica; pode antecipar lembrete |
| Colaborador | `diego@petcare.com.br` | PetCare | `/painel/receita` | O mesmo, com os dados da PetCare — serve para ver o isolamento entre clínicas |
| Veterinário | `helena@vidaanimal.com.br` | Vida Animal | `/painel/receita` | As mesmas telas de gestão do colaborador |
| Tutor (Thor) | `camila.ferreira@exemplo.com` | Vida Animal | `/tutor` | Só os próprios pets, a caixa de lembretes e a conversa com o agente |
| Tutor (Nala) | `roberto.almeida@exemplo.com` | Vida Animal | `/tutor` | O mesmo, para o outro pet |

Nenhum tutor alcança as telas de gestão, e nenhum membro da equipe alcança `/tutor/**` —
a separação está em `SecurityConfig` e o mapa completo de rota por perfil está em
[Mapa dos Requisitos](#-mapa-dos-requisitos). **Entrar com `diego@petcare.com.br` é a
forma mais rápida de ver o multi-tenancy funcionando:** os números do painel mudam
inteiros, porque ele é de outra clínica.

O fluxo completo atravessa duas telas e dois logins — use uma janela anônima
para a segunda sessão, senão uma derruba a outra. Entre como Patricia ou
Helena, registre uma consulta para o Thor e veja a obrigação nascer; entre como
Camila em `/tutor/pets/{id}`, converse com o agente e agende; volte para a
Helena e o agendamento está na agenda.

Base semeada antes de 2026-09-04 carrega o hash quebrado da V8 antiga — a **V10**
corrige as linhas, basta subir a aplicação. Se o login falhar num banco **resemeado**
depois disso, o problema é outro e está descrito em
[Repair de migration](#️-repair-de-migration-que-cria-objeto-plsql-exige-um-segundo-passo):
a procedure do seed pode ter ficado com o corpo antigo.

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

### Rodando os testes

A suíte tem **129 testes** e passa inteira, **sem nenhum pulado**. Os testes de
integração exigem o Oracle configurado nas variáveis de ambiente; sem elas, as classes
que precisam de banco são desabilitadas por `@EnabledIfEnvironmentVariable` e o resto
roda normalmente.

```bash
# com o Oracle do docker-compose de pé
export SPRING_DATASOURCE_URL='jdbc:oracle:thin:@localhost:1521/PETFLOWDB'
export SPRING_DATASOURCE_USERNAME=petflow
export SPRING_DATASOURCE_PASSWORD="$APP_USER_PASSWORD"   # definida no .env, nunca commitada
export JWT_SECRET='qualquer-segredo-com-mais-de-32-caracteres!!'

./mvnw test                                    # tudo
./mvnw test -Dtest='Agente*,Guardrail*'        # só o agente
./mvnw test -Dtest='*IntegracaoTest'           # só o que toca o banco
```

Nenhum teste chama a API do provedor de LLM: o diálogo roda contra um stub HTTP
(`MockRestServiceServer`), então a suíte não gasta cota nem exige chave.

> **Teste pulado conta como falha aqui.** Os testes de integração fabricam o próprio
> cenário — clínica, tutor, veterinário, pet e obrigação — dentro da transação, que é
> desfeita ao final (`CenarioClinico`, em `src/test/.../support/`). Nenhum deles depende
> de o banco já ter o dado certo, e por isso nenhum se pula em silêncio.

---

## ⚙️ Variáveis de Ambiente

| Variável | Descrição | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC do Oracle | — (obrigatória) |
| `SPRING_DATASOURCE_USERNAME` | Usuário Oracle | — (obrigatória) |
| `SPRING_DATASOURCE_PASSWORD` | Senha Oracle | — (obrigatória) |
| `JWT_SECRET` | Secret HS256 (mín. 32 chars) | — (obrigatória) |
| `CORS_ALLOWED_ORIGINS` | Origens autorizadas a chamar `/api` de um navegador, separadas por vírgula | vazio (nenhuma) |
| `GEMINI_API_KEY` | Chave do Gemini — **o provedor ativo** do agente | vazio (agente responde 503) |
| `ANTHROPIC_API_KEY` | Chave da Anthropic, para o provedor alternativo | vazio |
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

## 🏗️ Arquitetura da Solução

![Arquitetura ACR + ACI](docs/arquitetura_sprint3.png)

A solução usa **containerização completa** — aplicação e banco, cada um em seu
próprio Azure Container Instance — com as imagens vindas de um Azure Container
Registry privado. Todos os recursos são criados por Azure CLI, nenhum pelo Portal.

### Recursos

| Recurso | Nome | Configuração |
|---|---|---|
| Resource Group | `rg-petflow-rm561940` | Brazil South |
| Container Registry | `acrpetflowrm561940` | SKU Basic, admin habilitado |
| Key Vault | `kv-petflow-rm561940` | 3 segredos gerados em runtime |
| ACI — Banco | `rm561940-aci-db` | Oracle XE 21c · 2 vCPU / 4 GB · porta 1521 |
| ACI — Aplicação | `rm561940-aci-app` | Spring Boot / Java 21 · 1 vCPU / 2 GB · porta 8080 |

### Fluxos

| # | Fluxo | O que acontece |
|---|---|---|
| 1 | Desenvolvedor → Azure | Os scripts `01` a `06` criam todos os recursos via Azure CLI |
| 2 | Desenvolvedor → ACR | `docker build` e `docker push` das duas imagens |
| 3 | Scripts → Key Vault | As três senhas são geradas com `openssl rand` e guardadas no cofre |
| 4 | ACR → ACI Banco | O container puxa a imagem e recebe as senhas por variável de ambiente segura |
| 5 | ACR → ACI Aplicação | Idem, com a senha do banco e o segredo do JWT |
| 6 | Aplicação → Banco | Conexão JDBC pelo **FQDN público** na porta 1521 |
| 7 | Aplicação → Banco | O Flyway aplica 15 migrations e cria as 37 tabelas no primeiro boot |
| 8 | Usuário final → Aplicação | HTTP/REST pelo FQDN público na porta 8080 |

### Três decisões que o diagrama registra

**Dois container groups separados, não um multi-container.** Como não compartilham
rede interna, a aplicação alcança o banco pelo **FQDN público** — é a seta 6, e por
isso ela aparece destacada. Não há `localhost` em lugar nenhum da configuração.

**O container da aplicação roda como `appuser` (uid 999), nunca root.** Requisito
8.2 do enunciado. A imagem base é `eclipse-temurin:21-jre-jammy`, que tem shell —
sem shell não haveria `az container exec` para provar o não-root na gravação.

**O banco não tem volume.** O spike de 25–26/08 mostrou que o Oracle sobre Azure
Files falha com `ORA-01990` no primeiro boot, e a validação local de 09/09 confirmou
que sem mount o boot é limpo. O enunciado não exige persistência em disco — exige
evidenciar o dado gravado via `SELECT`. **Consequência: recriar o ACI do banco zera
os dados.**

---

## 🚀 Deploy na Azure — ACR + ACI (How to)

> Esta é a entrega da **Sprint 3**: containerização completa (App e Banco) com
> Azure Container Registry e Azure Container Instance, todos os recursos criados
> via Azure CLI. O roteiro abaixo é executável de ponta a ponta — o vídeo da
> entrega segue exatamente estes passos.

### Pré-requisitos

- Azure CLI autenticado (`az login`)
- Docker em execução
- Git Bash, WSL ou terminal Unix-like
- `openssl` e `curl` no PATH

### Passo 0 — Clonar o repositório

```bash
git clone https://github.com/olavoneves/clyvo-vet_api-java.git
cd clyvo-vet_api-java
```

### Passo 1 — Conferir as variáveis

Todos os nomes de recurso vivem em `scripts/00_variables.sh`. **Nenhuma senha
está lá** — as três são geradas em runtime no passo 3 e guardadas no Key Vault.

```bash
cat scripts/00_variables.sh
```

### Passo 2 — Infraestrutura base

```bash
bash scripts/01_resource-group.sh    # Resource Group
bash scripts/02_acr.sh               # Azure Container Registry (Basic)
```

### Passo 3 — Segredos

```bash
bash scripts/03_key-vault.sh
```

Gera com `openssl rand` e grava no cofre: senha do SYS do Oracle, senha do
usuário `petflow` e o segredo de assinatura do JWT. Os valores nunca são
impressos nem gravados em disco.

### Passo 4 — Build e push das imagens 

```bash
bash scripts/04_build-push.sh
```

Os comandos que este script executa:

```bash
az acr login --name acrpetflowrm561940

docker build --platform linux/amd64 \
    -t acrpetflowrm561940.azurecr.io/rm561940-db-petflow:v1 ./db

docker build --platform linux/amd64 \
    -t acrpetflowrm561940.azurecr.io/rm561940-app-petflow:v1 .

docker push acrpetflowrm561940.azurecr.io/rm561940-db-petflow:v1
docker push acrpetflowrm561940.azurecr.io/rm561940-app-petflow:v1

az acr repository list --name acrpetflowrm561940 --output table
```

> `--platform linux/amd64` é obrigatório: o ACI só executa amd64, e uma imagem
> arm64 falha com `exec format error`.

### Passo 5 — Banco de dados no ACI

```bash
bash scripts/05_aci-db.sh
```

Cria o container group do Oracle XE (2 vCPU / 4 GB) e espera o log chegar em
`DATABASE IS READY TO USE!`. Leva cerca de 2 minutos.

### Passo 6 — Aplicação no ACI

```bash
bash scripts/06_aci-app.sh
```

Descobre o FQDN público do banco, monta a URL JDBC e sobe a aplicação. No
primeiro boot o Flyway aplica 15 migrations e cria as 37 tabelas.

### Passo 7 — Bootstrap dos dados iniciais

```bash
bash scripts/07_bootstrap.sh
```

**Passo obrigatório.** O Flyway cria o schema, mas o banco nasce sem dados — e
o usuário `master` só é criado se já existir uma clínica. O script cria a
clínica pela rota pública, reinicia o app para o `DataInitializer` rodar e
confirma o login.

### Passo 8 — Smoke tests

```bash
bash scripts/08_smoke-tests.sh
```

Exercita o CRUD completo em `/api/tutores` e `/api/pets` contra o **FQDN
público**, incluindo o 409 ao tentar apagar um tutor com pet vinculado.

### Passo 9 — Evidência no banco

O enunciado exige demonstrar cada operação do CRUD **por SELECT dentro do banco**.
O banco fica exposto na porta 1521 com FQDN público, então qualquer cliente Oracle
conecta nele de fora — o que também prova que não é um banco local.

**SQL Developer** (ou DBeaver, SQLcl — qualquer cliente Oracle):

| Campo | Valor |
|---|---|
| Tipo de conexão | Básico |
| Hostname | `rm561940-db-petflow.brazilsouth.azurecontainer.io` |
| Porta | `1521` |
| **Nome do serviço** | `PETFLOWDB` — serviço, **não** SID |
| Usuário | `petflow` |
| Senha | recupere do Key Vault: |

```bash
az keyvault secret show --vault-name kv-petflow-rm561940   --name oracle-app-password --query value -o tsv
```

Conectado, o arquivo [`docs/EVIDENCIAS_VIDEO.sql`](docs/EVIDENCIAS_VIDEO.sql) traz
todas as consultas na ordem, pareadas com os `curl` que as antecedem.

A primeira delas confirma que a conexão é com o container na Azure:

```sql
SELECT SYS_CONTEXT('USERENV','SERVER_HOST') AS servidor,
       SYS_CONTEXT('USERENV','CON_NAME')    AS pdb,
       USER                                  AS usuario
  FROM dual;
```

**Alternativa por linha de comando**, se preferir não usar cliente gráfico:

```bash
az container exec   --resource-group rg-petflow-rm561940   --name rm561940-aci-db   --exec-command "/bin/bash"

# dentro do container:
sqlplus petflow/<senha>@localhost:1521/PETFLOWDB
```

> `az container exec` abre um websocket que **exige TTY real**: rode de um
> terminal interativo, não de script.

### Passo 10 — Encerramento

Ao terminar a sessão, pare os ACIs para não consumir a quota:

```bash
az container stop -g rg-petflow-rm561940 -n rm561940-aci-app
az container stop -g rg-petflow-rm561940 -n rm561940-aci-db
```

Para remover tudo (**somente após a nota sair**):

```bash
bash scripts/99_cleanup.sh
```

---

## 🖥️ Scripts DevOps

| Script | Finalidade |
|---|---|
| `scripts/00_variables.sh` | Nomes dos recursos e funções auxiliares. Carregado com `source` pelos demais. **Sem senhas** |
| `scripts/01_resource-group.sh` | Cria o Resource Group com tags |
| `scripts/02_acr.sh` | Cria o Azure Container Registry (Basic, admin habilitado) |
| `scripts/03_key-vault.sh` | Cria o Key Vault e gera os três segredos com `openssl rand` |
| `scripts/04_build-push.sh` | `docker build` + `docker push` das duas imagens para o ACR |
| `scripts/05_aci-db.sh` | ACI do Oracle XE — 2 vCPU / 4 GB, sem volume |
| `scripts/06_aci-app.sh` | ACI da API — 1 vCPU / 2 GB, conecta no banco pelo FQDN público |
| `scripts/07_bootstrap.sh` | Cria a clínica, reinicia o app e confirma o login |
| `scripts/08_smoke-tests.sh` | CRUD completo nas duas tabelas via FQDN público |
| `scripts/99_cleanup.sh` | Remove o Resource Group (confirmação digitada) |

### Documentação do banco

| Arquivo | Conteúdo |
|---|---|
| `docs/script_bd.sql` | DDL do núcleo da solução — tabelas, colunas, chaves, constraints e comentários, mais a carga mínima de demonstração e as consultas de evidência |
| `src/main/resources/db/migration/` | As 15 migrations Flyway (V0 → V13) que criam as 37 tabelas do schema completo |
| `docs/VALIDACAO_LOCAL.md` | Evidências da validação em Docker local, antes do deploy |
| `docs/EVIDENCIAS_VIDEO.sql` | Consultas da gravação, pareadas com os `curl` que as antecedem |

Todos são idempotentes: verificam se o recurso existe antes de criar e podem
ser reexecutados sem destruir o ambiente.

### Dockerfiles

| Arquivo | Imagem |
|---|---|
| `Dockerfile` | API Java — multi-stage (Maven → JRE), roda como `appuser` (uid 999), não root |
| `db/Dockerfile` | Oracle XE 21c — sem senha em `ENV`, sem `init.sql` (o schema é do Flyway) |

### Execução local (Docker Compose)

```bash
cp .env.example .env      # preencha as senhas
docker compose up -d
```


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
**Challenge:** Clyvo Vet — Sprint 3 (2026)