#!/bin/bash
# ============================================================
#  CLYVO VET - Script de Validacao
#  Verifica se todos os recursos foram criados corretamente
# ============================================================

RESOURCE_GROUP="rg-clyvovet-devops"
VM_NAME="vm-clyvovet"
PASS=0
FAIL=0

echo ""
echo "============================================="
echo "  CLYVO VET - VALIDACAO DA INFRAESTRUTURA"
echo "============================================="
echo ""


# ----------------------------------------
#  1) RESOURCE GROUP
# ----------------------------------------

echo ">>> [1/7] Verificando Resource Group..."
RG_STATE=$(az group show --name $RESOURCE_GROUP --query "properties.provisioningState" --output tsv 2>/dev/null)

if [ "$RG_STATE" == "Succeeded" ]; then
  echo "    OK - Resource Group '$RESOURCE_GROUP' existe"
  PASS=$((PASS+1))
else
  echo "    FALHA - Resource Group nao encontrado"
  FAIL=$((FAIL+1))
fi


# ----------------------------------------
#  2) MAQUINA VIRTUAL
# ----------------------------------------

echo ">>> [2/7] Verificando VM..."
VM_STATE=$(az vm get-instance-view --name $VM_NAME --resource-group $RESOURCE_GROUP --query "instanceView.statuses[1].displayStatus" --output tsv 2>/dev/null)

if [ "$VM_STATE" == "VM running" ]; then
  echo "    OK - VM '$VM_NAME' esta rodando"
  PASS=$((PASS+1))
else
  echo "    FALHA - VM nao esta rodando (estado: $VM_STATE)"
  FAIL=$((FAIL+1))
fi


# ----------------------------------------
#  3) IP PUBLICO
# ----------------------------------------

echo ">>> [3/7] Verificando IP publico..."
IP_PUBLICO=$(az vm show --name $VM_NAME --resource-group $RESOURCE_GROUP --show-details --query publicIps --output tsv 2>/dev/null)

if [ -n "$IP_PUBLICO" ]; then
  echo "    OK - IP publico: $IP_PUBLICO"
  PASS=$((PASS+1))
else
  echo "    FALHA - IP publico nao encontrado"
  FAIL=$((FAIL+1))
fi


# ----------------------------------------
#  4) PORTA 8080 (App Java)
# ----------------------------------------

echo ">>> [4/7] Verificando porta 8080..."
PORTA_8080=$(az network nsg rule list --resource-group $RESOURCE_GROUP --nsg-name "${VM_NAME}NSG" --query "[?destinationPortRange=='8080'].access" --output tsv 2>/dev/null)

if [ "$PORTA_8080" == "Allow" ]; then
  echo "    OK - Porta 8080 aberta"
  PASS=$((PASS+1))
else
  echo "    FALHA - Porta 8080 nao encontrada"
  FAIL=$((FAIL+1))
fi


# ----------------------------------------
#  5) PORTA 1521 (Oracle)
# ----------------------------------------

echo ">>> [5/7] Verificando porta 1521..."
PORTA_1521=$(az network nsg rule list --resource-group $RESOURCE_GROUP --nsg-name "${VM_NAME}NSG" --query "[?destinationPortRange=='1521'].access" --output tsv 2>/dev/null)

if [ "$PORTA_1521" == "Allow" ]; then
  echo "    OK - Porta 1521 aberta"
  PASS=$((PASS+1))
else
  echo "    FALHA - Porta 1521 nao encontrada"
  FAIL=$((FAIL+1))
fi


# ----------------------------------------
#  6) DOCKER INSTALADO
# ----------------------------------------

echo ">>> [6/7] Verificando Docker na VM..."
DOCKER_CHECK=$(az vm run-command invoke --resource-group $RESOURCE_GROUP --name $VM_NAME --command-id RunShellScript --scripts "docker --version" --query "value[0].message" --output tsv 2>/dev/null)

if echo "$DOCKER_CHECK" | grep -q "Docker version"; then
  DOCKER_VERSION=$(echo "$DOCKER_CHECK" | grep "Docker version" | head -1)
  echo "    OK - $DOCKER_VERSION"
  PASS=$((PASS+1))
else
  echo "    FALHA - Docker nao instalado"
  FAIL=$((FAIL+1))
fi


# ----------------------------------------
#  7) FERRAMENTAS (Git, nano, curl, wget, htop)
# ----------------------------------------

echo ">>> [7/7] Verificando ferramentas..."
TOOLS_CHECK=$(az vm run-command invoke --resource-group $RESOURCE_GROUP --name $VM_NAME --command-id RunShellScript --scripts "git --version && nano --version | head -1 && curl --version | head -1 && wget --version | head -1 && htop --version" --query "value[0].message" --output tsv 2>/dev/null)

if echo "$TOOLS_CHECK" | grep -q "git version"; then
  echo "    OK - Git, nano, curl, wget, htop instalados"
  PASS=$((PASS+1))
else
  echo "    FALHA - Algumas ferramentas nao encontradas"
  FAIL=$((FAIL+1))
fi


# ----------------------------------------
#  RESULTADO FINAL
# ----------------------------------------

TOTAL=$((PASS+FAIL))
echo ""
echo "============================================="
echo "  RESULTADO: $PASS/$TOTAL verificacoes OK"
echo "============================================="
echo ""

if [ $FAIL -eq 0 ]; then
  echo "  TUDO CERTO! Infraestrutura pronta."
  echo ""
  echo "  SSH:         ssh azureuser@$IP_PUBLICO"
  echo "  App Java:    http://$IP_PUBLICO:8080"
  echo "  Oracle DB:   $IP_PUBLICO:1521/PETFLOWDB"
else
  echo "  ATENCAO: $FAIL verificacao(oes) falharam."
  echo "  Revise os itens marcados como FALHA."
fi

echo ""
echo "============================================="
echo ""
