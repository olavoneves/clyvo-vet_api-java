#!/bin/bash
# ============================================================
#  CLYVO VET - Script de Remocao
#  Deleta todos os recursos criados na Azure
#  *** EXECUTAR SOMENTE APOS A ENTREGA ***
# ============================================================

RESOURCE_GROUP="rg-clyvovet-devops"

echo ""
echo "============================================="
echo "  CLYVO VET - REMOCAO DE RECURSOS"
echo "============================================="
echo ""
echo "  Resource Group: $RESOURCE_GROUP"
echo ""
echo "  ATENCAO: Isso vai deletar TUDO dentro"
echo "  desse resource group (VM, disco, IP, rede)."
echo ""
read -p "  Tem certeza? (s/n): " CONFIRMA
echo ""

if [ "$CONFIRMA" == "s" ] || [ "$CONFIRMA" == "S" ]; then
  echo ">>> Deletando resource group '$RESOURCE_GROUP'..."
  az group delete --name $RESOURCE_GROUP --yes --no-wait
  echo ""
  echo ">>> Remocao iniciada em background."
  echo ">>> Para verificar, execute:"
  echo "    az group show --name $RESOURCE_GROUP --query properties.provisioningState --output tsv"
  echo ""
  echo ">>> Quando retornar erro 'not found', tudo foi removido."
else
  echo ">>> Cancelado. Nenhum recurso foi removido."
fi

echo ""
