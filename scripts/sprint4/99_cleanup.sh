#!/usr/bin/env bash
#
# 99 - Remove todos os recursos Azure da Sprint 4.
#
# QUANDO EXECUTAR: depois que o video estiver publicado, a nota saiu e
# ninguem mais precisa da solucao no ar. Antes disso, mantenha de pe: e a
# rede de seguranca para regravar.
#
# O que NAO e removido aqui: o projeto no Azure DevOps. O professor corrige
# olhando as pipelines e o historico de execucoes - apagar o projeto antes
# da nota e zero.
#
# Exige confirmacao digitada: `az group delete` e irreversivel.
#
# ATENCAO - Key Vault: o cofre fica em soft delete por 90 dias depois
# disso. Recriar um cofre com o mesmo nome falha ate ser purgado.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
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
echo "Depois disto, o CD falha ate os scripts 01 a 05 serem rodados de novo."
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
echo ""
echo "Lembrete: o cofre '${KEYVAULT_NAME}' ficara em soft delete por 90 dias."
