#!/usr/bin/env bash
#
# 09 - Exercita o CRUD completo contra o Web App publicado pelo CD.
#
# Todas as chamadas usam a URL publica do Web App (https) - nenhuma
# chamada de loopback. Entrega em localhost e zero de nota.
#
# O par de tabelas e TB_CLV_TUTOR 1:N TB_CLV_PET. Cada operacao imprime o
# status HTTP esperado ao lado do obtido; a evidencia no banco e feita por
# SELECT, com docs/EVIDENCIAS_VIDEO.sql.
#
# As rotas vivem sob /api - o prefixo e aplicado pelo WebMvcConfig, nao
# esta escrito nos controllers.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando curl
exigir_comando openssl

titulo "09 - SMOKE TESTS EM NUVEM"

API="${WEBAPP_URL}/api"

# A senha do master vem do Key Vault (02_key-vault.sh): o app a recebe por
# MASTER_PASSWORD e nenhum literal no repositorio vira credencial.
LOGIN_MASTER="{\"email\":\"master@clyvovet.com\",\"senha\":\"$(ler_segredo "${SECRET_MASTER}")\",\"tipo\":\"COLABORADOR\"}"

# Os tutores criados aqui sao descartaveis: senha aleatoria por execucao, para
# nenhuma senha fixa ficar no repositorio.
SENHA_TUTOR="qa-$(openssl rand -hex 6)"
echo "Endpoint base: ${API}"

CORPO="$(dirname "$0")/.smoke-resposta.tmp"
trap 'rm -f "${CORPO}"' EXIT

FALHAS=0

# chamar <esperado> <rotulo> <args do curl...>
chamar() {
    local esperado="$1"; shift
    local rotulo="$1"; shift
    local obtido
    obtido=$(curl -s -o "${CORPO}" -w "%{http_code}" "$@")

    if [[ "${obtido}" == "${esperado}" ]]; then
        printf "  [ OK ] %-46s %s\n" "${rotulo}" "${obtido}"
    else
        printf "  [FALHA] %-45s esperado %s, obtido %s\n" "${rotulo}" "${esperado}" "${obtido}"
        echo "         corpo: $(head -c 300 "${CORPO}")"
        FALHAS=$((FALHAS + 1))
    fi
}

titulo "SAUDE DA APLICACAO"
chamar 200 "GET    /actuator/health" "${WEBAPP_URL}/actuator/health"

titulo "AUTENTICACAO"

# O access token expira em 15 minutos. Se este script passar disso, um 401
# no meio nao e bug - e so reautenticar.
chamar 200 "POST   /auth/login" \
    -X POST "${API}/auth/login" \
    -H "Content-Type: application/json" \
    -d "${LOGIN_MASTER}"

TOKEN=$(grep -o '"accessToken":"[^"]*' "${CORPO}" | cut -d'"' -f4)
if [[ -z "${TOKEN}" ]]; then
    echo "ERRO: nao foi possivel obter o token. Rode 08_bootstrap.sh antes." >&2
    exit 1
fi
AUTH="Authorization: Bearer ${TOKEN}"
echo "         token obtido (${#TOKEN} chars)"

ID_CLINICA=$(curl -s -H "${AUTH}" "${API}/clinicas" \
             | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "         clinica em uso: id=${ID_CLINICA}"

titulo "TABELA TUTOR (lado 1 do relacionamento)"

# O e-mail e UNIQUE. Derivar do timestamp mantem o script reexecutavel.
SUFIXO="$(date +%s | tail -c 7)"
EMAIL_TESTE="smoke${SUFIXO}@exemplo.com"

chamar 201 "POST   /tutores" \
    -X POST "${API}/tutores" \
    -H "Content-Type: application/json" \
    -d "{\"nome\":\"Maria Smoke Test\",\"email\":\"${EMAIL_TESTE}\",
         \"telefone\":\"11988887777\",\"senha\":\"${SENHA_TUTOR}\",
         \"clinicaId\":${ID_CLINICA}}"

ID_TUTOR=$(grep -o '"id":[0-9]*' "${CORPO}" | head -1 | cut -d: -f2)
echo "         id_tutor criado: ${ID_TUTOR}"

chamar 200 "GET    /tutores (lista)"        -H "${AUTH}" "${API}/tutores"
chamar 200 "GET    /tutores/${ID_TUTOR}"    -H "${AUTH}" "${API}/tutores/${ID_TUTOR}"

# PUT reusa o DTO do POST: senha e clinicaId sao obrigatorios no corpo,
# alem dos campos alterados. Um PUT parcial devolve 422.
chamar 200 "PUT    /tutores/${ID_TUTOR}" \
    -X PUT "${API}/tutores/${ID_TUTOR}" \
    -H "${AUTH}" -H "Content-Type: application/json" \
    -d "{\"nome\":\"Maria Smoke Test ALTERADO\",\"email\":\"${EMAIL_TESTE}\",
         \"telefone\":\"11988886666\",\"senha\":\"${SENHA_TUTOR}\",
         \"clinicaId\":${ID_CLINICA}}"

titulo "TABELA PET (lado N do relacionamento)"

ID_RACA=$(curl -s -H "${AUTH}" "${API}/racas?size=1" \
          | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "         raca escolhida: id=${ID_RACA}"

chamar 201 "POST   /pets" \
    -X POST "${API}/pets" \
    -H "${AUTH}" -H "Content-Type: application/json" \
    -d "{\"nome\":\"Luna\",\"dtNascimento\":\"2022-03-15\",\"sexo\":\"F\",
         \"porte\":\"GRANDE\",\"castrado\":true,\"status\":\"ATIVO\",
         \"tutorId\":${ID_TUTOR},\"racaId\":${ID_RACA}}"

ID_PET=$(grep -o '"id":[0-9]*' "${CORPO}" | head -1 | cut -d: -f2)
echo "         id_pet criado: ${ID_PET}"

chamar 200 "GET    /pets (lista)"      -H "${AUTH}" "${API}/pets"
chamar 200 "GET    /pets/${ID_PET}"    -H "${AUTH}" "${API}/pets/${ID_PET}"

chamar 200 "PUT    /pets/${ID_PET}" \
    -X PUT "${API}/pets/${ID_PET}" \
    -H "${AUTH}" -H "Content-Type: application/json" \
    -d "{\"nome\":\"Luna Bela\",\"dtNascimento\":\"2022-03-15\",\"sexo\":\"F\",
         \"porte\":\"GRANDE\",\"castrado\":true,\"status\":\"ATIVO\",
         \"tutorId\":${ID_TUTOR},\"racaId\":${ID_RACA}}"

titulo "INTEGRIDADE REFERENCIAL"

# Apagar tutor com pet vinculado precisa dar 409, nunca 500: quem trata e
# o RestControllerAdvice, convertendo DataIntegrityViolationException em
# resposta legivel.
chamar 409 "DELETE /tutores/${ID_TUTOR} (com pet vinculado)" \
    -X DELETE "${API}/tutores/${ID_TUTOR}" -H "${AUTH}"

titulo "EXCLUSAO NA ORDEM CORRETA"

chamar 204 "DELETE /pets/${ID_PET}"       -X DELETE "${API}/pets/${ID_PET}" -H "${AUTH}"
chamar 404 "GET    /pets/${ID_PET}"       -H "${AUTH}" "${API}/pets/${ID_PET}"
chamar 204 "DELETE /tutores/${ID_TUTOR}"  -X DELETE "${API}/tutores/${ID_TUTOR}" -H "${AUTH}"
chamar 404 "GET    /tutores/${ID_TUTOR}"  -H "${AUTH}" "${API}/tutores/${ID_TUTOR}"

titulo "RESUMO"
if [[ "${FALHAS}" -eq 0 ]]; then
    echo "Todos os testes passaram."
    echo ""
    echo "CRUD completo exercitado nas duas tabelas relacionadas:"
    echo "  TB_CLV_TUTOR 1:N TB_CLV_PET"
else
    echo "${FALHAS} teste(s) falharam." >&2
    exit 1
fi
