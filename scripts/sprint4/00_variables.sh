#!/usr/bin/env bash
#
# Projeto Clyvo Vet / PetFlow
# FIAP - DevOps Tools & Cloud Computing - Sprint 4 (Azure DevOps + CI/CD)
#
#   RM561940 - Pedro Henrique Dias Franca
#   RM562248 - Felipe Conte
#   RM562906 - Altamir Lima
#   RM563558 - Olavo Porto Neves
#   RM564495 - Luiz Gustavo Goncalves
#
# Centraliza os nomes de todos os recursos. NENHUMA SENHA AQUI.
# As senhas sao geradas em 02_key-vault.sh, ficam no Key Vault e chegam a
# aplicacao pelo variable group secreto do Azure DevOps (06_azure-devops.sh).
#
# Este arquivo nao e executado diretamente: e carregado com `source` pelos
# demais scripts.
#
set -euo pipefail

# O Git Bash converte argumentos que comecam com "/" em caminhos do Windows
# (/subscriptions/... vira C:/Program Files/Git/subscriptions/...). Os
# scripts so passam caminhos para o az, que entende os dois formatos.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

# --- identificacao -----------------------------------------------------
export RM="rm561940"

# --- assinatura --------------------------------------------------------
# Os recursos vivem na assinatura Azure for Students do Olavo (RM563558),
# onde o RM561940 e Owner. A assinatura original do RM561940 esgotou o
# credito em 10/2026 e ficou desabilitada (somente leitura), com os
# recursos antigos presos nela - por isso os nomes globais levam SUFIXO.
export SUBSCRIPTION_ID="065b7acf-df9f-4ebf-8e01-829240bb8bea"
export SUFIXO="s4b"

# --- regiao ------------------------------------------------------------
# A policy "Allowed resource deployment regions" da assinatura Azure for
# Students permite chilecentral, eastus2, southcentralus, northcentralus
# e brazilsouth. brazilsouth e a de menor latencia para a gravacao.
export LOCATION="brazilsouth"

# --- grupo de recursos -------------------------------------------------
export RESOURCE_GROUP="rg-petflow-s4-${RM}"

# --- cofre e registry --------------------------------------------------
# Nomes com SUFIXO: os de Key Vault, ACR, Web App e DNS do ACI sao unicos
# no Azure inteiro, e os da primeira tentativa seguem presos na assinatura
# desabilitada.
export KEYVAULT_NAME="kv-petflow-${SUFIXO}-${RM}"
export ACR_NAME="acrpetflow${SUFIXO}${RM}"

# --- banco de dados (Oracle XE em ACI) ---------------------------------
# A imagem do Oracle e importada do Docker Hub para o ACR (03_acr.sh): o
# ACI puxa do nosso registry e nao esbarra no rate limit do Docker Hub.
# E a mesma imagem de db/Dockerfile, que so acrescenta o EXPOSE 1521.
export ORACLE_SOURCE_IMAGE="docker.io/gvenzl/oracle-xe:21-slim"
export IMAGE_DB="${RM}-db-petflow"
export IMAGE_TAG="v1"

export ACI_DB="${RM}-aci-db"
# Rotulo DNS: precisa ser unico dentro da regiao.
export DNS_DB="${RM}-${SUFIXO}-db-petflow"
export DB_FQDN="${DNS_DB}.${LOCATION}.azurecontainer.io"

# ORACLE_DATABASE pede ao entrypoint da imagem que CRIE um PDB com este
# nome. Nao pode ser "XEPDB1", que ja vem pronto na imagem (ORA-65012).
# Mesmo nome do docker-compose.yaml.
export ORACLE_DATABASE="PETFLOWDB"
export APP_USER="petflow"

# Formato thin com barra dupla e nome de SERVICO (nao SID).
export JDBC_URL="jdbc:oracle:thin:@//${DB_FQDN}:1521/${ORACLE_DATABASE}"

# --- aplicacao (Azure Web App, Java SE) --------------------------------
# App Service sem container: o CD publica o JAR gerado pelo CI.
export APP_SERVICE_PLAN="asp-petflow-${RM}"
export WEBAPP_NAME="${RM}-petflow-api-${SUFIXO}"
export WEBAPP_RUNTIME="JAVA:21-java21"
export WEBAPP_URL="https://${WEBAPP_NAME}.azurewebsites.net"

# --- nomes dos segredos no Key Vault -----------------------------------
# Apenas os NOMES. Os valores nunca aparecem em arquivo.
export SECRET_ORACLE_PASSWORD="oracle-sys-password"
export SECRET_APP_PASSWORD="oracle-app-password"
export SECRET_JWT="app-jwt-secret"
export SECRET_MASTER="master-password"

# --- Azure DevOps ------------------------------------------------------
# A organizacao e criada pelo portal (dev.azure.com): nao ha comando de CLI
# para isso. Ajuste ADO_ORG para o nome da organizacao criada.
#
# Organizacao do Olavo (RM563558), com o RM561940 como Project Collection
# Administrator. O agente hospedado gratuito (1 job, 1800 min/mes) so vale
# com a organizacao vinculada a uma assinatura ativa: a RM561940 ficou presa
# a assinatura desabilitada e passou a ter 0 jobs hospedados.
export ADO_ORG="${ADO_ORG:-RM563558}"
export ADO_ORG_URL="https://dev.azure.com/${ADO_ORG}"
export ADO_PROJECT="Sprint 4 - Azure DevOps"

export PROFESSOR_NOME="Joao Carlos Menk"
# Conta de login (UPN) do professor no Entra ID da FIAP, conforme o
# slide 8/8 do enunciado. profjoao.menk@fiap.com.br e so o e-mail dela:
# convidar por ele falha com "user from outside your directory".
export PROFESSOR_EMAIL="PF0841@fiap.com.br"
export TURMA="2TDSR"

# Service connections: criadas pelo portal (06_azure-devops.sh explica por
# que) e referenciadas pelos YAMLs com estes nomes exatos.
export SC_GITHUB="sc-github-petflow"
export SC_AZURE="sc-azure-petflow"
# Identidade que a SC_AZURE usa (06b_service-connection-azure.sh): managed
# identity com federacao, sem App Registration nem segredo.
export MANAGED_IDENTITY="id-petflow-ado-${RM}"

# Variable group com os segredos que o CD grava nas app settings.
export VARIABLE_GROUP="petflow-secrets"

# Pipelines: o CD referencia o CI pelo nome (resources.pipelines.source).
export GITHUB_REPO_URL="https://github.com/olavoneves/clyvo-vet_api-java"
export GITHUB_BRANCH="main"
export PIPELINE_CI="petflow-ci"
export PIPELINE_CD="petflow-cd"
# Environment do job de deploy (cd.yml): guarda o historico de deploys.
export ADO_ENVIRONMENT="petflow-producao"
export YAML_CI="pipelines/ci.yml"
export YAML_CD="pipelines/cd.yml"

# --- tags --------------------------------------------------------------
export TAG_DISCIPLINA="devops-tools-cloud-computing"
export TAG_SPRINT="sprint4-azure-devops"
export TAG_PROJETO="clyvo-vet"
TAGS=("disciplina=${TAG_DISCIPLINA}" "sprint=${TAG_SPRINT}" "projeto=${TAG_PROJETO}")

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
# O tr tira o \r que o az.cmd do Windows acrescenta ao fim da linha.
ler_segredo() {
    az keyvault secret show \
        --vault-name "${KEYVAULT_NAME}" \
        --name "$1" \
        --query value \
        --output tsv | tr -d '\r'
}

# --- assinatura ativa --------------------------------------------------
# Todo script que carrega este arquivo passa a operar na assinatura certa,
# mesmo que o `az login` tenha outra como padrao (a desabilitada aparece
# como default no login do RM561940).
if command -v az >/dev/null 2>&1; then
    az account set --subscription "${SUBSCRIPTION_ID}"
fi
