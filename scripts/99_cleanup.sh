#!/usr/bin/env bash
#
# 99 - Remove todos os recursos do projeto.
#
# QUANDO EXECUTAR: depois que o video estiver publicado e o link no PDF.
#
# Diferente do CP1, a Sprint 3 nao exige evidencia de remocao - e tambem
# nao exige que a solucao continue no ar. A correcao e feita assistindo
# ao video; o acesso que o enunciado cobra e ao repositorio e ao video.
#
# Antes disso, mantenha de pe: e a rede de seguranca para regravar. Dois
# ACIs ligados custam cerca de R$ 21 por dia, entao nao deixe correndo
# depois que a entrega saiu.
#
# Exige confirmacao digitada: `az group delete` e irreversivel.
#
# ATENCAO - Key Vault: o cofre fica em soft delete por 90 dias depois
# disso. Recriar um cofre com o mesmo nome falha ate ser purgado:
#     az keyvault purge --name kv-petflow-rm561940
#
# Projeto Clyvo Vet - Sprint 3 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "99 - REMOCAO DOS RECURSOS"
echo "Grupo de recursos: ${RESOURCE_GROUP}"

if ! az group show --name "${RESOURCE_GROUP}" >/dev/null 2>&1; then
    echo ""
    echo "O grupo '${RESOURCE_GROUP}' nao existe. Nada a fazer."
    exit 0
fi

echo ""
echo "Sera removido:"
az resource list --resource-group "${RESOURCE_GROUP}" \
    --query "[].{nome:name, tipo:type}" --output table

echo ""
echo "Isto e IRREVERSIVEL. O banco nao tem volume: os dados vao junto."
echo ""
read -r -p "Digite o nome do grupo para confirmar: " CONFIRMACAO

if [[ "${CONFIRMACAO}" != "${RESOURCE_GROUP}" ]]; then
    echo "Confirmacao nao confere. Nada foi removido."
    exit 1
fi

titulo "REMOVENDO"
az group delete --name "${RESOURCE_GROUP}" --yes --no-wait
echo "Remocao iniciada em background (--no-wait)."

titulo "VERIFICACAO"
echo "Acompanhe com:"
echo "    az group list --output table"
echo "    az container list --output table"
echo ""
echo "Lembrete: o cofre '${KEYVAULT_NAME}' ficara em soft delete por 90 dias."
