#!/bin/bash
# ============================================================
#  CLYVO VET — DevOps Tools & Cloud Computing (1o Sprint)
#  Script Azure CLI — Provisionamento de Infraestrutura
#  FIAP — Challenge 2025/2026
# ============================================================


# ============================================================
#  VARIAVEIS
# ============================================================

RESOURCE_GROUP="rg-clyvovet-devops"
LOCATION="northcentralus"
VM_NAME="vm-clyvovet"
ADMIN_USER="azureuser"
ADMIN_PASSWORD='PetFlow@2026!'
VM_SIZE="Standard_B2als_v2"
IMAGE="Ubuntu2204"


# ============================================================
#  1.1) CRIAR RESOURCE GROUP
# ============================================================

echo ""
echo ">>> [1/6] Criando Resource Group: $RESOURCE_GROUP"
echo ""

az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION


# ============================================================
#  1.2) CRIAR MAQUINA VIRTUAL LINUX
# ============================================================

echo ""
echo ">>> [2/6] Criando VM: $VM_NAME ($VM_SIZE - Ubuntu 22.04)"
echo ""

az vm create \
  --resource-group $RESOURCE_GROUP \
  --name $VM_NAME \
  --image $IMAGE \
  --size $VM_SIZE \
  --admin-username $ADMIN_USER \
  --admin-password "$ADMIN_PASSWORD" \
  --authentication-type password \
  --public-ip-sku Standard


# ============================================================
#  1.3) ABRIR PORTAS
# ============================================================

echo ""
echo ">>> [3/6] Abrindo portas (8080 = App Java, 1521 = Oracle)"
echo ""

az vm open-port \
  --resource-group $RESOURCE_GROUP \
  --name $VM_NAME \
  --port 8080 \
  --priority 1010

az network nsg rule create \
  --resource-group $RESOURCE_GROUP \
  --nsg-name "${VM_NAME}NSG" \
  --name allow-oracle-1521 \
  --priority 1020 \
  --destination-port-ranges 1521 \
  --access Allow \
  --protocol Tcp \
  --direction Inbound


# ============================================================
#  1.4) INSTALAR DOCKER
# ============================================================

echo ""
echo ">>> [4/6] Instalando Docker na VM..."
echo ""

az vm run-command invoke \
  --resource-group $RESOURCE_GROUP \
  --name $VM_NAME \
  --command-id RunShellScript \
  --scripts '
    sudo apt-get update -y &&
    sudo apt-get install -y ca-certificates curl gnupg lsb-release &&
    sudo install -m 0755 -d /etc/apt/keyrings &&
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg &&
    sudo chmod a+r /etc/apt/keyrings/docker.gpg &&
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null &&
    sudo apt-get update -y &&
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin &&
    sudo usermod -aG docker azureuser &&
    sudo systemctl start docker &&
    sudo systemctl enable docker
  '


# ============================================================
#  1.5) INSTALAR FERRAMENTAS (Git, nano, curl, wget, htop)
# ============================================================

echo ""
echo ">>> [5/6] Instalando ferramentas (Git, nano, curl, wget, htop)..."
echo ""

az vm run-command invoke \
  --resource-group $RESOURCE_GROUP \
  --name $VM_NAME \
  --command-id RunShellScript \
  --scripts '
    sudo apt-get install -y git nano curl wget htop
  '


# ============================================================
#  1.6) EXIBIR DADOS DE ACESSO
# ============================================================

echo ""
echo ">>> [6/6] Obtendo IP publico da VM..."
echo ""

IP_PUBLICO=$(az vm show \
  --resource-group $RESOURCE_GROUP \
  --name $VM_NAME \
  --show-details \
  --query publicIps \
  --output tsv)

echo ""
echo "============================================="
echo "  CLYVO VET — VM PROVISIONADA COM SUCESSO"
echo "============================================="
echo ""
echo "  IP Publico:  $IP_PUBLICO"
echo "  Usuario:     $ADMIN_USER"
echo "  Senha:       $ADMIN_PASSWORD"
echo ""
echo "  SSH:         ssh $ADMIN_USER@$IP_PUBLICO"
echo "  App Java:    http://$IP_PUBLICO:8080"
echo "  Oracle DB:   $IP_PUBLICO:1521/PETFLOWDB"
echo ""
echo "============================================="
echo ""


# ============================================================
#  REMOCAO DOS RECURSOS (EXECUTAR AO FINAL DA ENTREGA)
#  *** OBRIGATORIO — Descomentar e rodar apos a entrega ***
# ============================================================
# az group delete --name rg-clyvovet-devops --yes --no-wait
# ============================================================
