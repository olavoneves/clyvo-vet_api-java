#!/usr/bin/env bash
#
# 07 - Registra as pipelines de CI e de CD no Azure DevOps.
#
# Os YAMLs ja estao no repositorio (pipelines/ci.yml e pipelines/cd.yml);
# este script apenas aponta o Azure DevOps para eles.
#
#   petflow-ci : dispara a cada push na main, compila, roda os testes,
#                publica o resultado dos testes e o artefato (JAR).
#   petflow-cd : dispara quando o petflow-ci termina com sucesso, ou seja,
#                quando ha um novo artefato; faz o deploy no Web App.
#
# O CD referencia o CI pelo NOME (resources.pipelines.source em cd.yml):
# por isso o CI precisa se chamar exatamente ${PIPELINE_CI}.
#
# --skip-first-run: o primeiro run fica para o push de verdade, que e o
# que o video precisa mostrar.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "07 - PIPELINES"
az devops configure --defaults organization="${ADO_ORG_URL}" project="${ADO_PROJECT}"

# Le o id de uma service connection pelo nome; aborta se nao existir.
id_service_connection() {
    local id
    id=$(az devops service-endpoint list \
             --query "[?name=='$1'].id | [0]" --output tsv | tr -d '\r')
    if [[ -z "${id}" ]]; then
        echo "ERRO: service connection '$1' nao encontrada." >&2
        echo "Crie-a pelo portal (instrucoes no final do 06_azure-devops.sh)." >&2
        exit 1
    fi
    printf '%s' "${id}"
}

titulo "SERVICE CONNECTIONS"
ID_SC_GITHUB=$(id_service_connection "${SC_GITHUB}")
ID_SC_AZURE=$(id_service_connection "${SC_AZURE}")

# Libera as duas conexoes para todas as pipelines do projeto. Sem isso o
# primeiro run para em "This pipeline needs permission to access a
# resource" e espera um clique - o que quebraria o disparo automatico.
for id in "${ID_SC_GITHUB}" "${ID_SC_AZURE}"; do
    az devops service-endpoint update --id "${id}" --enable-for-all true --output none
done
echo "  ${SC_GITHUB} .......... ${ID_SC_GITHUB}"
echo "  ${SC_AZURE} ........... ${ID_SC_AZURE}"

# Cria uma pipeline YAML, se ainda nao existir.
criar_pipeline() {
    local nome="$1"
    local yaml="$2"
    local descricao="$3"

    if az pipelines show --name "${nome}" >/dev/null 2>&1; then
        echo "  ${nome} .......... ja existe (mantida)"
        return
    fi

    az pipelines create \
        --name "${nome}" \
        --description "${descricao}" \
        --repository "${GITHUB_REPO_URL}" \
        --repository-type github \
        --branch "${GITHUB_BRANCH}" \
        --yml-path "${yaml}" \
        --service-connection "${ID_SC_GITHUB}" \
        --skip-first-run true \
        --output none
    echo "  ${nome} .......... criada"
}

titulo "REGISTRO DAS PIPELINES"
# A ordem importa: o CD so resolve resources.pipelines depois que o CI existe.
criar_pipeline "${PIPELINE_CI}" "${YAML_CI}" "CI: build, testes automaticos e publicacao do artefato"
criar_pipeline "${PIPELINE_CD}" "${YAML_CD}" "CD: deploy do artefato no Azure Web App"

titulo "RESULTADO"
az pipelines list --query "[].{pipeline:name, id:id, pasta:path}" --output table

cat <<EOF

Para disparar o fluxo completo, faca um push na branch ${GITHUB_BRANCH}
(no video, altere CODIGO - mudar so o README desconta pontos). Acompanhe em:
  ${ADO_ORG_URL}/$(printf '%s' "${ADO_PROJECT}" | sed 's/ /%20/g')/_build

Depois do primeiro deploy, rode 08_bootstrap.sh.
EOF
