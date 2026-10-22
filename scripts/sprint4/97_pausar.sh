#!/usr/bin/env bash
#
# 97 - PAUSA os recursos que cobram por hora, sem apagar nada.
#
# QUANDO EXECUTAR: ao terminar cada sessao de trabalho/gravacao. O credito
# do Azure for Students acabou em 10/2026 com o ACI (2 vCPU / 4 GB) e o
# plano B1 ligados 24 h por dia.
#
#   - ACI do banco ..... parado (para de cobrar)
#   - Web App .......... parado
#   - App Service Plan . B1 -> F1 (gratuito). Parar o Web App NAO para a
#                        cobranca do plano: o B1 cobra enquanto existir.
#   - ACR e Key Vault .. ficam como estao (custo de centavos por dia)
#
# ATENCAO: o ACI nao tem volume. Ao religar (98_retomar.sh) o Oracle sobe
# zerado e o bootstrap (08) precisa rodar de novo.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "97 - PAUSANDO RECURSOS"

echo "Parando o Web App ${WEBAPP_NAME}..."
az webapp stop --resource-group "${RESOURCE_GROUP}" --name "${WEBAPP_NAME}"

# O F1 nao aceita Always On: desligar antes de rebaixar o plano.
echo "Desligando Always On..."
az webapp config set --resource-group "${RESOURCE_GROUP}" \
    --name "${WEBAPP_NAME}" --always-on false --output none

echo "Rebaixando ${APP_SERVICE_PLAN} para F1..."
az appservice plan update --resource-group "${RESOURCE_GROUP}" \
    --name "${APP_SERVICE_PLAN}" --sku F1 --output none

echo "Parando o ACI ${ACI_DB}..."
az container stop --resource-group "${RESOURCE_GROUP}" --name "${ACI_DB}"

titulo "VERIFICACAO"
az appservice plan show --resource-group "${RESOURCE_GROUP}" \
    --name "${APP_SERVICE_PLAN}" --query "{plano:name, sku:sku.name}" --output table
az webapp show --resource-group "${RESOURCE_GROUP}" \
    --name "${WEBAPP_NAME}" --query "{webapp:name, estado:state}" --output table
az container show --resource-group "${RESOURCE_GROUP}" \
    --name "${ACI_DB}" --query "{aci:name, estado:instanceView.state}" --output table
