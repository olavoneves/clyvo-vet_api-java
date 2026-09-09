#!/usr/bin/env bash
#
# 01 - Cria o Grupo de Recursos que abriga todo o projeto.
#
# Requisito 8.1 do enunciado: todos os recursos criados via Azure CLI
# (-30 se algum for criado pelo Portal).
#
# Projeto Clyvo Vet - Sprint 3 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "01 - GRUPO DE RECURSOS"
echo "Nome    : ${RESOURCE_GROUP}"
echo "Regiao  : ${LOCATION}"

if az group show --name "${RESOURCE_GROUP}" >/dev/null 2>&1; then
    echo ""
    echo "AVISO: o grupo '${RESOURCE_GROUP}' ja existe. Nada a fazer."
else
    az group create \
        --name "${RESOURCE_GROUP}" \
        --location "${LOCATION}" \
        --tags disciplina="${TAG_DISCIPLINA}" \
               sprint="${TAG_SPRINT}" \
               projeto="${TAG_PROJETO}" \
        --output table
fi

titulo "RESULTADO"
az group show --name "${RESOURCE_GROUP}" \
    --query "{nome:name, regiao:location, estado:properties.provisioningState}" \
    --output table
