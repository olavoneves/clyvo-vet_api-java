#!/usr/bin/env bash
#
# 08 - Bootstrap dos dados iniciais, depois do PRIMEIRO deploy do CD.
#
# POR QUE ESTE SCRIPT EXISTE
#
# O Flyway cria as 37 tabelas, mas o banco nasce SEM DADOS. E ha uma
# ordem obrigatoria, descoberta na validacao local da Sprint 3:
#
#   1. O DataInitializer so cria o usuario master se ja existir clinica.
#      Sem clinica ele registra no log:
#        "Nenhuma clinica cadastrada: usuario master nao criado."
#
#   2. As procedures PR_CLV_SEED_* da V8 tambem exigem clinica, e num
#      banco vazio quebram com ORA-01400.
#
# Entao: cria a clinica pela rota publica, reinicia o Web App para o
# DataInitializer rodar de novo, confirma o login e carrega dois tutores
# com um pet cada, para o CRUD do video ter dado significativo na tela.
#
# Roda UMA vez por banco. Deploys seguintes do CD nao precisam dele: os
# dados ficam no Oracle enquanto o ACI do banco nao for recriado.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az
exigir_comando curl
exigir_comando openssl

titulo "08 - BOOTSTRAP DOS DADOS INICIAIS"

API="${WEBAPP_URL}/api"

# A senha do master vem do Key Vault (02_key-vault.sh): o app a recebe por
# MASTER_PASSWORD e nenhum literal no repositorio vira credencial.
LOGIN_MASTER="{\"email\":\"master@clyvovet.com\",\"senha\":\"$(ler_segredo "${SECRET_MASTER}")\",\"tipo\":\"COLABORADOR\"}"

# Os tutores criados aqui sao descartaveis: senha aleatoria por execucao, para
# nenhuma senha fixa ficar no repositorio.
SENHA_TUTOR="qa-$(openssl rand -hex 6)"

echo "Endpoint base: ${API}"

# Ao lado do script, e nao em /tmp: no Git Bash do Windows o curl.exe nao
# enxerga o /tmp emulado.
CORPO="$(dirname "$0")/.bootstrap-resposta.tmp"
trap 'rm -f "${CORPO}"' EXIT

titulo "PASSO 1 - CLINICA (rota publica, sem token)"

STATUS=$(curl -s -o "${CORPO}" -w "%{http_code}" \
    -X POST "${API}/clinicas" \
    -H "Content-Type: application/json" \
    -d '{"nome":"Clinica PetFlow Central","cnpj":"12345678000199",
         "logradouro":"Av Paulista","numero":"1000","bairro":"Bela Vista",
         "cidade":"Sao Paulo","estado":"SP","cep":"01310100",
         "telefone":"1133334444"}')

if [[ "${STATUS}" == "201" ]]; then
    ID_CLINICA=$(grep -o '"id":[0-9]*' "${CORPO}" | head -1 | cut -d: -f2)
    echo "  clinica criada: id=${ID_CLINICA}"
elif [[ "${STATUS}" == "409" ]]; then
    echo "  clinica ja existe (409) - seguindo"
else
    echo "ERRO: POST /clinicas devolveu ${STATUS}." >&2
    echo "A aplicacao ja foi publicada pelo CD? Confira ${WEBAPP_URL}/actuator/health" >&2
    cat "${CORPO}" >&2
    exit 1
fi

titulo "PASSO 2 - RESTART DO WEB APP"
echo "O DataInitializer roda no startup: sem restart, o master nao nasce."

az webapp restart --resource-group "${RESOURCE_GROUP}" \
                  --name "${WEBAPP_NAME}" \
                  --output none

titulo "PASSO 3 - CONFIRMACAO DO LOGIN"
echo "Aguardando a aplicacao voltar e o login do master responder 200."
echo "Timeout: 5 minutos."

PRONTO=0
for tentativa in $(seq 1 30); do
    STATUS=$(curl -s -o "${CORPO}" -w "%{http_code}" --max-time 20 \
        -X POST "${API}/auth/login" \
        -H "Content-Type: application/json" \
        -d "${LOGIN_MASTER}" || true)
    if [[ "${STATUS}" == "200" ]]; then
        echo ""
        echo "  login OK apos aproximadamente $((tentativa * 10)) segundos - token emitido"
        PRONTO=1
        break
    fi
    printf "."
    sleep 10
done

if [[ "${PRONTO}" -eq 0 ]]; then
    echo ""
    echo "ERRO: o login do master nao respondeu 200 (ultimo status: ${STATUS})." >&2
    echo "Veja o log com:" >&2
    echo "    az webapp log tail -g ${RESOURCE_GROUP} -n ${WEBAPP_NAME}" >&2
    exit 1
fi

titulo "PASSO 4 - CARGA DE DEMONSTRACAO"
#
# A carga vem pela API, nao por INSERT direto: assim o video evidencia a
# integracao App <-> Banco. As procedures PR_CLV_SEED_* da V8 nao servem
# aqui - geram centenas de linhas e poluem o SELECT na tela.

TOKEN=$(grep -o '"accessToken":"[^"]*' "${CORPO}" | cut -d'"' -f4)
AUTH="Authorization: Bearer ${TOKEN}"

ID_CLINICA=$(curl -s -H "${AUTH}" "${API}/clinicas" \
             | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

ID_RACA=$(curl -s -H "${AUTH}" "${API}/racas?size=1" \
          | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

echo "  clinica=${ID_CLINICA}  raca=${ID_RACA}"

# Cria um tutor e ecoa o id. Devolve vazio se o e-mail ja existir.
criar_tutor() {
    local nome="$1" email="$2" fone="$3" canal="$4" status
    status=$(curl -s -o "${CORPO}" -w "%{http_code}" \
        -X POST "${API}/tutores" \
        -H "Content-Type: application/json" \
        -d "{\"nome\":\"${nome}\",\"email\":\"${email}\",\"telefone\":\"${fone}\",\"senha\":\"${SENHA_TUTOR}\",\"canalPreferencial\":\"${canal}\",\"clinicaId\":${ID_CLINICA}}")

    if [[ "${status}" == "201" ]]; then
        grep -o '"id":[0-9]*' "${CORPO}" | head -1 | cut -d: -f2
    fi
}

# Cria um pet vinculado ao tutor e ecoa o status HTTP.
criar_pet() {
    local nome="$1" nasc="$2" sexo="$3" porte="$4" castrado="$5" tutor="$6"
    curl -s -o "${CORPO}" -w "%{http_code}" \
        -X POST "${API}/pets" \
        -H "${AUTH}" -H "Content-Type: application/json" \
        -d "{\"nome\":\"${nome}\",\"dtNascimento\":\"${nasc}\",\"sexo\":\"${sexo}\",\"porte\":\"${porte}\",\"castrado\":${castrado},\"status\":\"ATIVO\",\"tutorId\":${tutor},\"racaId\":${ID_RACA}}"
}

ID_MARIA=$(criar_tutor "Maria Aparecida Silva" "maria.silva@exemplo.com" "11988887777" "APP")
ID_JOAO=$(criar_tutor "Joao Pedro Nascimento" "joao.nascimento@exemplo.com" "11977776666" "WHATSAPP")

if [[ -n "${ID_MARIA:-}" ]]; then
    echo "  tutor Maria Aparecida Silva ... id=${ID_MARIA}"
    echo "  pet Luna ...................... HTTP $(criar_pet 'Luna' '2022-03-15' 'F' 'GRANDE' 'true' "${ID_MARIA}")"
else
    echo "  tutor Maria ................... ja existia (mantido)"
fi

if [[ -n "${ID_JOAO:-}" ]]; then
    echo "  tutor Joao Pedro Nascimento ... id=${ID_JOAO}"
    echo "  pet Mingau .................... HTTP $(criar_pet 'Mingau' '2023-08-02' 'M' 'PEQUENO' 'false' "${ID_JOAO}")"
else
    echo "  tutor Joao .................... ja existia (mantido)"
fi

unset TOKEN AUTH

titulo "RESULTADO"
echo "Ambiente pronto para os smoke tests (09_smoke-tests.sh)."
echo ""
echo "Credenciais de demonstracao:"
echo "  master@clyvovet.com / segredo '${SECRET_MASTER}' do Key Vault (COLABORADOR)"
echo ""
echo "Nenhuma delas e credencial de producao."
