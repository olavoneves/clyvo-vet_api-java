#!/usr/bin/env bash
#
# 02 - Cria o Azure Container Registry (ACR).
#
# O enunciado exige ACR e ACI juntos na Opcao 1. As imagens do App e do
# Banco vem daqui, nunca do Docker Hub.
#
# SKU Basic: suficiente para duas imagens e mais barato que o Standard.
# admin-enabled: o ACI autentica no registry com usuario/senha de admin.
#
# Projeto Clyvo Vet - Sprint 3 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "02 - AZURE CONTAINER REGISTRY"
echo "Nome  : ${ACR_NAME}"
echo "SKU   : Basic"

if az acr show --name "${ACR_NAME}" --resource-group "${RESOURCE_GROUP}" >/dev/null 2>&1; then
    echo ""
    echo "AVISO: o registry '${ACR_NAME}' ja existe. Nada a fazer."
else
    az acr create \
        --resource-group "${RESOURCE_GROUP}" \
        --name "${ACR_NAME}" \
        --sku Basic \
        --location "${LOCATION}" \
        --public-network-enabled true \
        --admin-enabled true \
        --output table
fi

titulo "RESULTADO"
LOGIN_SERVER=$(az acr show --name "${ACR_NAME}" \
                           --resource-group "${RESOURCE_GROUP}" \
                           --query loginServer --output tsv)
echo "Login Server: ${LOGIN_SERVER}"
echo ""
echo "Use este valor nos comandos docker tag / docker push (script 04)."
