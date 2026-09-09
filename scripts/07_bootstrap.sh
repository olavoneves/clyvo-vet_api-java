#!/usr/bin/env bash
#
# 07 - Bootstrap dos dados iniciais.
#
# POR QUE ESTE SCRIPT EXISTE
#
# O Flyway cria as 37 tabelas, mas o banco nasce SEM DADOS. E ha uma
# ordem obrigatoria, descoberta na validacao local de 09/09:
#
#   1. O DataInitializer so cria o usuario master se ja existir clinica.
#      Sem clinica ele registra no log:
#        "Nenhuma clinica cadastrada: usuario master nao criado."
#
#   2. As procedures PR_CLV_SEED_* da V8 tambem exigem clinica: elas fazem
#      SELECT MIN(id_clinica) INTO v_clinica FROM TB_CLV_CLINICA, e num
#      banco vazio isso devolve NULL, quebrando com
#        ORA-01400: cannot insert NULL into ("TB_CLV_TUTOR"."ID_CLINICA")
#
# Entao: cria a clinica pela rota publica, reinicia o app para o
# DataInitializer rodar de novo, e confirma o login.
#
# Projeto Clyvo Vet - Sprint 3 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az
exigir_comando curl

titulo "07 - BOOTSTRAP DOS DADOS INICIAIS"

APP_FQDN=$(az container show \
               --resource-group "${RESOURCE_GROUP}" \
               --name "${ACI_APP}" \
               --query ipAddress.fqdn --output tsv)

if [[ -z "${APP_FQDN}" ]]; then
    echo "ERRO: nao foi possivel obter o FQDN do ACI '${ACI_APP}'." >&2
    echo "Execute 06_aci-app.sh antes deste script." >&2
    exit 1
fi

API="http://${APP_FQDN}:8080/api"
echo "Endpoint base: ${API}"

# Ao lado do script, e nao em /tmp: no Git Bash do Windows o curl.exe nao
# enxerga o /tmp emulado e o corpo iria para um caminho ilegivel daqui.
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
    cat "${CORPO}" >&2
    exit 1
fi

titulo "PASSO 2 - RESTART DO APP"
echo "O DataInitializer roda no startup: sem restart, o master nao nasce."

az container restart --resource-group "${RESOURCE_GROUP}" \
                     --name "${ACI_APP}" \
                     --output none

echo "Aguardando a aplicacao voltar. Timeout: 5 minutos."

PRONTO=0
for tentativa in $(seq 1 30); do
    if az container logs --resource-group "${RESOURCE_GROUP}" \
                         --name "${ACI_APP}" 2>/dev/null \
       | grep -q "Usuario master criado"; then
        echo ""
        echo "Master criado apos aproximadamente $((tentativa * 10)) segundos."
        PRONTO=1
        break
    fi
    printf "."
    sleep 10
done

if [[ "${PRONTO}" -eq 0 ]]; then
    echo ""
    echo "AVISO: nao encontrei 'Usuario master criado' no log." >&2
    echo "Se o master ja existia de uma execucao anterior, isso e esperado" >&2
    echo "- o DataInitializer sai cedo quando o e-mail ja esta cadastrado." >&2
    echo "O passo 3 confirma." >&2
fi

titulo "PASSO 3 - CONFIRMACAO DO LOGIN"

STATUS=$(curl -s -o "${CORPO}" -w "%{http_code}" \
    -X POST "${API}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"master@clyvovet.com","senha":"master","tipo":"COLABORADOR"}')

if [[ "${STATUS}" != "200" ]]; then
    echo "ERRO: login devolveu ${STATUS}, esperado 200." >&2
    cat "${CORPO}" >&2
    exit 1
fi

echo "  login OK - token emitido"

titulo "PASSO 4 - CARGA DE DEMONSTRACAO"
#
# Item 5 do enunciado: inserir e manipular pelo menos 2 linhas com
# conteudo significativo nas duas tabelas do CRUD (-20 se faltar).
#
# A carga vem pela API, nao por INSERT direto: assim o video evidencia a
# integracao App <-> Banco, que e o que o item 9.3 cobra. As procedures
# PR_CLV_SEED_* da V8 nao servem aqui - geram centenas de linhas e
# poluem o SELECT na tela.

TOKEN=$(curl -s -X POST "${API}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"master@clyvovet.com","senha":"master","tipo":"COLABORADOR"}' \
    | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

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
        -d "{\"nome\":\"${nome}\",\"email\":\"${email}\",\"telefone\":\"${fone}\",\"senha\":\"Clyvo@2026\",\"canalPreferencial\":\"${canal}\",\"clinicaId\":${ID_CLINICA}}")

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
        -d "{\"nome\":\"${nome}\",\"dtNascimento\":\"${nasc}\",\"sexo\":\"${sexo}\",\"porte\":\"${porte}\",\"castrado\":${castrado},\"statusPet\":\"ATIVO\",\"tutorId\":${tutor},\"racaId\":${ID_RACA}}"
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
echo "Ambiente pronto para os smoke tests (08_smoke-tests.sh)."
echo ""
echo "Credenciais de demonstracao:"
echo "  master@clyvovet.com / master        (COLABORADOR, criado pelo app)"
echo "  usuarios do seed    / Clyvo@2026    (se PR_CLV_SEED_* for executado)"
echo ""
echo "Nenhuma delas e credencial de producao."
