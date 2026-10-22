#!/usr/bin/env bash
#
# 98 - RETOMA os recursos pausados por 97_pausar.sh.
#
# Depois deste script, rode 08_bootstrap.sh e 09_smoke-tests.sh: o Oracle
# volta zerado (ACI sem volume) e o Flyway recria o schema no boot do app.
#
# Lembre de rodar 97_pausar.sh ao terminar.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "98 - RETOMANDO RECURSOS"

# Banco primeiro: o Oracle leva alguns minutos para abrir o PDB e o app
# falha no Flyway se subir antes.
echo "Iniciando o ACI ${ACI_DB}..."
az container start --resource-group "${RESOURCE_GROUP}" --name "${ACI_DB}"

echo "Voltando ${APP_SERVICE_PLAN} para B1..."
az appservice plan update --resource-group "${RESOURCE_GROUP}" \
    --name "${APP_SERVICE_PLAN}" --sku B1 --output none

echo "Religando Always On..."
az webapp config set --resource-group "${RESOURCE_GROUP}" \
    --name "${WEBAPP_NAME}" --always-on true --output none

echo ""
echo "Aguarde o Oracle ficar pronto antes de iniciar o Web App:"
echo "    az container logs -g ${RESOURCE_GROUP} -n ${ACI_DB} | grep 'DATABASE IS READY'"
read -r -p "Enter quando aparecer 'DATABASE IS READY TO USE'... "

echo "Iniciando o Web App ${WEBAPP_NAME}..."
az webapp start --resource-group "${RESOURCE_GROUP}" --name "${WEBAPP_NAME}"

titulo "PROXIMOS PASSOS"
echo "    ./08_bootstrap.sh"
echo "    ./09_smoke-tests.sh"
echo "    (boot do app no B1 leva ~4 min)"
