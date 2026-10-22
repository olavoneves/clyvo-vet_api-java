#!/usr/bin/env bash
#
# 01 - Cria o Grupo de Recursos que abriga todo o projeto.
#
# Todos os recursos da Sprint 4 nascem por Azure CLI: Key Vault, ACR, ACI
# do banco, App Service Plan e Web App.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "01 - GRUPO DE RECURSOS"
echo "Nome    : ${RESOURCE_GROUP}"
echo "Regiao  : ${LOCATION}"
echo "Assinatura: ${SUBSCRIPTION_ID}"

titulo "REGISTRO DOS SERVICOS UTILIZADOS"
# Como no criar-infra.sh das aulas: numa assinatura nova os resource
# providers nascem desregistrados e o primeiro `create` de cada servico
# falha com MissingSubscriptionRegistration.
for provedor in Microsoft.KeyVault Microsoft.ContainerRegistry \
                Microsoft.ContainerInstance Microsoft.Web \
                Microsoft.ManagedIdentity; do
    az provider register --namespace "${provedor}" --wait --output none
    echo "  ${provedor} ...... registrado"
done

titulo "GRUPO"
if az group show --name "${RESOURCE_GROUP}" >/dev/null 2>&1; then
    echo ""
    echo "AVISO: o grupo '${RESOURCE_GROUP}' ja existe. Nada a fazer."
else
    az group create \
        --name "${RESOURCE_GROUP}" \
        --location "${LOCATION}" \
        --tags "${TAGS[@]}" \
        --output table
fi

titulo "RESULTADO"
az group show --name "${RESOURCE_GROUP}" \
    --query "{nome:name, regiao:location, estado:properties.provisioningState}" \
    --output table
