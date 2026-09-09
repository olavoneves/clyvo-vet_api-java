#!/usr/bin/env bash
#
# Projeto Clyvo Vet / PetFlow
# FIAP - DevOps Tools & Cloud Computing - Sprint 3
#
#   RM561940 - Pedro Henrique Dias Franca
#   RM562248 - Felipe Conte
#   RM562906 - Altamir Lima
#   RM563558 - Olavo Porto Neves
#   RM564495 - Luiz Gustavo Goncalves
#
# Centraliza os nomes de todos os recursos. NENHUMA SENHA AQUI.
# As senhas sao geradas em 03_key-vault.sh e lidas do cofre em runtime.
#
# Este arquivo nao e executado diretamente: e carregado com `source` pelos
# demais scripts.
#
set -euo pipefail

# --- identificacao -----------------------------------------------------
export RM="rm561940"

# --- regiao ------------------------------------------------------------
# A policy "Allowed resource deployment regions" (definicao
# b86dabb9-b578-4d7b-b842-3b45e95769a1) permite: canadacentral,
# northcentralus, brazilsouth, eastus e chilecentral. brazilsouth tem
# 6 Standard Cores livres - precisamos de 3 (2 no banco, 1 no app) - e
# e a regiao de menor latencia para os comandos interativos da gravacao.
export LOCATION="brazilsouth"

# --- grupo de recursos -------------------------------------------------
export RESOURCE_GROUP="rg-petflow-${RM}"

# --- registry e cofre --------------------------------------------------
# Nao ha Storage Account: o banco roda sem volume. A validacao local de
# 09/09 confirmou que o ORA-01990 do spike vinha do mount CIFS do Azure
# Files - sem volume, o Oracle sobe limpo. Ver docs/VALIDACAO_LOCAL.md.
export ACR_NAME="acrpetflow${RM}"
export KEYVAULT_NAME="kv-petflow-${RM}"

# --- imagens -----------------------------------------------------------
export IMAGE_DB="${RM}-db-petflow"
export IMAGE_APP="${RM}-app-petflow"
export IMAGE_TAG="v1"

# --- instancias de container -------------------------------------------
# Dois container groups distintos, nao um multi-container.
export ACI_DB="${RM}-aci-db"
export ACI_APP="${RM}-aci-app"

# Rotulos DNS: precisam ser unicos dentro da regiao.
export DNS_DB="${RM}-db-petflow"
export DNS_APP="${RM}-app-petflow"

# --- banco de dados ----------------------------------------------------
# XEPDB1 e o PDB padrao da imagem gvenzl/oracle-xe.
export ORACLE_DATABASE="XEPDB1"
export APP_USER="petflow"

# --- nomes dos segredos no Key Vault -----------------------------------
# Apenas os NOMES. Os valores nunca aparecem em arquivo.
export SECRET_ORACLE_PASSWORD="oracle-sys-password"
export SECRET_APP_PASSWORD="oracle-app-password"
export SECRET_JWT="app-jwt-secret"

# --- tags --------------------------------------------------------------
export TAG_DISCIPLINA="devops-tools-cloud-computing"
export TAG_SPRINT="sprint3-acr-aci"
export TAG_PROJETO="clyvo-vet"

# --- derivados ---------------------------------------------------------
export ACR_LOGIN_SERVER="${ACR_NAME}.azurecr.io"

# --- utilitarios usados pelos demais scripts ---------------------------

# Imprime um cabecalho de secao.
titulo() {
    echo ""
    echo "======================================================================"
    echo "  $*"
    echo "======================================================================"
}

# Aborta se um comando obrigatorio nao estiver disponivel.
exigir_comando() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "ERRO: comando '$1' nao encontrado no PATH." >&2
        exit 1
    fi
}

# Le um segredo do Key Vault. O valor vai para stdout e nunca para disco.
ler_segredo() {
    az keyvault secret show \
        --vault-name "${KEYVAULT_NAME}" \
        --name "$1" \
        --query value \
        --output tsv
}
