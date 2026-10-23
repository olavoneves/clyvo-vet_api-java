#!/usr/bin/env bash
#
# 06b - Cria a service connection Azure Resource Manager (SC_AZURE) pelo
#       CLI, com workload identity federation sobre uma MANAGED IDENTITY.
#
# POR QUE NAO PELO PORTAL: o modo "automatico" do portal cria um App
# Registration no Entra ID da FIAP, e aluno nao tem permissao para isso. A
# managed identity nasce na assinatura (onde somos Owner) e a federacao
# dispensa senha: o Azure DevOps troca um token proprio por um token do
# Entra a cada execucao. Nenhum segredo e criado, guardado ou expira.
#
#   1. managed identity no grupo de recursos
#   2. papel Contributor SO no grupo (nao na assinatura inteira)
#   3. service connection no Azure DevOps apontando para a identidade
#   4. credencial federada na identidade, com o issuer/subject que o
#      Azure DevOps informa para essa service connection
#
# A service connection do GitHub (SC_GITHUB) fica no portal: depende do
# OAuth do dono do repositorio.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "06b - SERVICE CONNECTION AZURE (${SC_AZURE})"
echo "Identidade : ${MANAGED_IDENTITY}"
echo "Escopo     : ${RESOURCE_GROUP} (Contributor)"

az devops configure --defaults organization="${ADO_ORG_URL}" project="${ADO_PROJECT}"

# --- 1. managed identity -----------------------------------------------
titulo "PASSO 1 - MANAGED IDENTITY"
if az identity show --name "${MANAGED_IDENTITY}" \
                    --resource-group "${RESOURCE_GROUP}" >/dev/null 2>&1; then
    echo "AVISO: a identidade '${MANAGED_IDENTITY}' ja existe."
else
    az identity create \
        --name "${MANAGED_IDENTITY}" \
        --resource-group "${RESOURCE_GROUP}" \
        --location "${LOCATION}" \
        --tags "${TAGS[@]}" \
        --output none
    echo "  identidade ................... criada"
fi

CLIENT_ID=$(az identity show --name "${MANAGED_IDENTITY}" --resource-group "${RESOURCE_GROUP}" \
                --query clientId --output tsv | tr -d '\r')
PRINCIPAL_ID=$(az identity show --name "${MANAGED_IDENTITY}" --resource-group "${RESOURCE_GROUP}" \
                --query principalId --output tsv | tr -d '\r')
TENANT_ID=$(az account show --query tenantId --output tsv | tr -d '\r')
NOME_ASSINATURA=$(az account show --query name --output tsv | tr -d '\r')

# --- 2. papel ----------------------------------------------------------
titulo "PASSO 2 - PAPEL CONTRIBUTOR NO GRUPO"
ESCOPO="/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RESOURCE_GROUP}"
if az role assignment list --assignee "${PRINCIPAL_ID}" --scope "${ESCOPO}" \
        --role Contributor --query "[0].id" --output tsv 2>/dev/null | grep -q .; then
    echo "AVISO: o papel ja esta atribuido."
else
    # A identidade recem-criada demora alguns segundos para aparecer no
    # Entra ID; --assignee-principal-type evita a consulta que falharia.
    az role assignment create \
        --assignee-object-id "${PRINCIPAL_ID}" \
        --assignee-principal-type ServicePrincipal \
        --role Contributor \
        --scope "${ESCOPO}" \
        --output none
    echo "  Contributor em ${RESOURCE_GROUP} ... atribuido"
fi

# --- 3. service connection ---------------------------------------------
titulo "PASSO 3 - SERVICE CONNECTION NO AZURE DEVOPS"
ID_SC=$(az devops service-endpoint list \
            --query "[?name=='${SC_AZURE}'].id | [0]" --output tsv | tr -d '\r')

if [[ -n "${ID_SC}" ]]; then
    echo "AVISO: a service connection '${SC_AZURE}' ja existe (id ${ID_SC})."
else
    ID_PROJETO=$(az devops project show --project "${ADO_PROJECT}" \
                     --query id --output tsv | tr -d '\r')

    # Ao lado do script, e nao em /tmp: no Git Bash do Windows o az.cmd nao
    # enxerga o /tmp emulado. O arquivo nao tem segredo nenhum.
    CONFIG="$(dirname "$0")/.service-endpoint.tmp.json"
    trap 'rm -f "${CONFIG}"' EXIT
    cat > "${CONFIG}" <<EOF
{
  "name": "${SC_AZURE}",
  "type": "AzureRM",
  "url": "https://management.azure.com/",
  "description": "Deploy do PetFlow no ${RESOURCE_GROUP} (managed identity ${MANAGED_IDENTITY})",
  "authorization": {
    "scheme": "WorkloadIdentityFederation",
    "parameters": {
      "tenantid": "${TENANT_ID}",
      "serviceprincipalid": "${CLIENT_ID}"
    }
  },
  "data": {
    "environment": "AzureCloud",
    "scopeLevel": "Subscription",
    "subscriptionId": "${SUBSCRIPTION_ID}",
    "subscriptionName": "${NOME_ASSINATURA}",
    "creationMode": "Manual",
    "identityType": "ManagedIdentity"
  },
  "isShared": false,
  "isReady": true,
  "serviceEndpointProjectReferences": [
    {
      "projectReference": { "id": "${ID_PROJETO}", "name": "${ADO_PROJECT}" },
      "name": "${SC_AZURE}",
      "description": "Deploy do PetFlow no ${RESOURCE_GROUP}"
    }
  ]
}
EOF
    ID_SC=$(az devops service-endpoint create \
                --service-endpoint-configuration "${CONFIG}" \
                --query id --output tsv | tr -d '\r')
    echo "  service connection ........... criada (id ${ID_SC})"
fi

# Equivale ao "Grant access permission to all pipelines" do portal.
az devops service-endpoint update --id "${ID_SC}" --enable-for-all true --output none
echo "  liberada para todas as pipelines"

# --- 4. credencial federada --------------------------------------------
titulo "PASSO 4 - CREDENCIAL FEDERADA"
# Uma credencial por organizacao: o subject identifica a service connection, e
# a mesma identidade pode atender conexoes de mais de uma organizacao.
# O issuer e o subject sao definidos pelo Azure DevOps para cada service
# connection: lidos dela em vez de montados a mao.
ISSUER=$(az devops service-endpoint show --id "${ID_SC}" \
             --query "authorization.parameters.workloadIdentityFederationIssuer" \
             --output tsv | tr -d '\r')
SUBJECT=$(az devops service-endpoint show --id "${ID_SC}" \
              --query "authorization.parameters.workloadIdentityFederationSubject" \
              --output tsv | tr -d '\r')
echo "  issuer  : ${ISSUER}"
echo "  subject : ${SUBJECT}"

if [[ -z "${ISSUER}" || -z "${SUBJECT}" ]]; then
    echo "ERRO: o Azure DevOps nao informou issuer/subject da federacao." >&2
    exit 1
fi

if az identity federated-credential show --name "ado-${ADO_ORG}-${SC_AZURE}" \
        --identity-name "${MANAGED_IDENTITY}" \
        --resource-group "${RESOURCE_GROUP}" >/dev/null 2>&1; then
    echo "AVISO: a credencial federada ja existe."
else
    az identity federated-credential create \
        --name "ado-${ADO_ORG}-${SC_AZURE}" \
        --identity-name "${MANAGED_IDENTITY}" \
        --resource-group "${RESOURCE_GROUP}" \
        --issuer "${ISSUER}" \
        --subject "${SUBJECT}" \
        --audiences "api://AzureADTokenExchange" \
        --output none
    echo "  credencial federada .......... criada"
fi

titulo "RESULTADO"
az devops service-endpoint list \
    --query "[].{nome:name, tipo:type, pronta:isReady, autenticacao:authorization.scheme}" \
    --output table

echo ""
echo "PROXIMO PASSO: 07_pipelines.sh."
