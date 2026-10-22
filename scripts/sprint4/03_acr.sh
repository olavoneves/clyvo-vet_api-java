#!/usr/bin/env bash
#
# 03 - Cria o Azure Container Registry e importa a imagem do Oracle.
#
# O banco roda em container na nuvem (ACI), opcao aceita pelo enunciado
# ("Bancos em Containers na nuvem ... Oracle (Container em Nuvem)").
#
# `az acr import` copia a imagem do Docker Hub para o ACR do lado do
# servidor: nao precisa de Docker na maquina e nao trafega os ~750 MB pela
# rede local. A imagem importada e a mesma de db/Dockerfile, que so
# acrescenta o EXPOSE 1521 a gvenzl/oracle-xe:21-slim.
#
# SKU Basic: suficiente para uma imagem e mais barato que o Standard.
# admin-enabled: o ACI autentica no registry com usuario/senha de admin.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "03 - AZURE CONTAINER REGISTRY"
echo "Nome    : ${ACR_NAME}"
echo "SKU     : Basic"
echo "Imagem  : ${ORACLE_SOURCE_IMAGE} -> ${IMAGE_DB}:${IMAGE_TAG}"

if az acr show --name "${ACR_NAME}" --resource-group "${RESOURCE_GROUP}" >/dev/null 2>&1; then
    echo ""
    echo "AVISO: o registry '${ACR_NAME}' ja existe. Nada a fazer."
else
    az acr create \
        --resource-group "${RESOURCE_GROUP}" \
        --name "${ACR_NAME}" \
        --sku Basic \
        --location "${LOCATION}" \
        --admin-enabled true \
        --tags "${TAGS[@]}" \
        --output table
fi

titulo "IMPORTACAO DA IMAGEM DO BANCO"
if az acr repository show-tags --name "${ACR_NAME}" --repository "${IMAGE_DB}" \
                               --output tsv 2>/dev/null | tr -d '\r' | grep -qx "${IMAGE_TAG}"; then
    echo "AVISO: ${IMAGE_DB}:${IMAGE_TAG} ja esta no registry. Nada a fazer."
else
    echo "Copiando do Docker Hub (lado do servidor, leva 1-3 minutos)..."
    az acr import \
        --name "${ACR_NAME}" \
        --source "${ORACLE_SOURCE_IMAGE}" \
        --image "${IMAGE_DB}:${IMAGE_TAG}" \
        --output none
    echo "  imagem importada."
fi

titulo "RESULTADO"
echo "Login Server: ${ACR_LOGIN_SERVER}"
echo ""
az acr repository show-tags --name "${ACR_NAME}" \
                            --repository "${IMAGE_DB}" \
                            --output table
