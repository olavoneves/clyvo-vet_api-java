#!/usr/bin/env bash
#
# 06 - Configura o projeto no Azure DevOps: projeto, acesso do professor e
#      variable group com os segredos da aplicacao.
#
# PRE-REQUISITOS (feitos uma unica vez, pelo portal):
#   1. Organizacao criada em https://dev.azure.com com o nome de ADO_ORG
#      (00_variables.sh). Nao existe comando de CLI para criar organizacao.
#   2. `az login` com a conta que e dona da organizacao. A extensao
#      azure-devops do az usa o mesmo login - nao precisa de PAT.
#
# O QUE O ENUNCIADO COBRA E ONDE ESTA AQUI
#   - Project Name "Sprint 4 - Azure DevOps", Description com professor e
#     integrantes, Visibility Private, Git, processo Scrum ........ passo 1
#   - Professor convidado com acesso Basic (sem isso a nota e zero) e
#     administrador do projeto ..................................... passo 2
#   - Variaveis de ambiente protegidas (usuarios, senhas e tokens) .. passo 3
#
# Os valores secretos saem do Key Vault direto para o Azure DevOps por
# variavel de ambiente (AZURE_DEVOPS_EXT_PIPELINE_VAR_*), que o az le sem
# que o valor apareca na linha de comando nem em disco.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "06 - AZURE DEVOPS"
echo "Organizacao : ${ADO_ORG_URL}"
echo "Projeto     : ${ADO_PROJECT}"

if ! az extension show --name azure-devops >/dev/null 2>&1; then
    az extension add --name azure-devops --only-show-errors
fi
az devops configure --defaults organization="${ADO_ORG_URL}"

# --- 1. projeto --------------------------------------------------------
titulo "PASSO 1 - PROJETO"

DESCRICAO="Projeto para entrega da Sprint 4 do professor ${PROFESSOR_NOME}. Integrantes: RM561940 - Pedro Henrique Dias Franca - ${TURMA}; RM562248 - Felipe Conte - ${TURMA}; RM562906 - Altamir Lima - ${TURMA}; RM563558 - Olavo Porto Neves - ${TURMA}; RM564495 - Luiz Gustavo Goncalves - ${TURMA}"

if az devops project show --project "${ADO_PROJECT}" >/dev/null 2>&1; then
    echo "AVISO: o projeto '${ADO_PROJECT}' ja existe. Nada a fazer."
else
    az devops project create \
        --name "${ADO_PROJECT}" \
        --description "${DESCRICAO}" \
        --visibility private \
        --source-control git \
        --process Scrum \
        --output none
    echo "  projeto ...................... criado"
fi

az devops configure --defaults organization="${ADO_ORG_URL}" project="${ADO_PROJECT}"

az devops project show --project "${ADO_PROJECT}" \
    --query "{projeto:name, visibilidade:visibility, processo:capabilities.processTemplate.templateName, controleVersao:capabilities.versioncontrol.sourceControlType}" \
    --output table

# --- 2. professor ------------------------------------------------------
titulo "PASSO 2 - ACESSO DO PROFESSOR"

# "express" e o nome interno do nivel de acesso Basic. Stakeholder nao
# enxerga Repos nem o detalhe das pipelines: o enunciado exige Basic.
if az devops user show --user "${PROFESSOR_EMAIL}" >/dev/null 2>&1; then
    echo "  ${PROFESSOR_EMAIL} ja esta na organizacao"
else
    az devops user add \
        --email-id "${PROFESSOR_EMAIL}" \
        --license-type express \
        --send-email-invite true \
        --output none
    echo "  ${PROFESSOR_EMAIL} ...... convidado (Basic)"
fi

GRUPO_ADMIN=$(az devops security group list --project "${ADO_PROJECT}" \
                  --query "graphGroups[?displayName=='Project Administrators'].descriptor | [0]" \
                  --output tsv | tr -d '\r')
az devops security group membership add \
    --group-id "${GRUPO_ADMIN}" \
    --member-id "${PROFESSOR_EMAIL}" \
    --output none 2>/dev/null || true
echo "  ${PROFESSOR_EMAIL} ...... em Project Administrators"

az devops user show --user "${PROFESSOR_EMAIL}" \
    --query "{usuario:user.mailAddress, acesso:accessLevel.licenseDisplayName}" \
    --output table

# --- 3. variable group -------------------------------------------------
titulo "PASSO 3 - VARIABLE GROUP '${VARIABLE_GROUP}'"

# DB_JDBC_URL nao e segredo (e o FQDN publico do ACI) e fica visivel.
# Usuario, senha e token vao como SECRETOS: aparecem com cadeado no
# Library, mascarados (***) nos logs, e nao podem ser lidos de volta.
ID_GRUPO=$(az pipelines variable-group list \
               --query "[?name=='${VARIABLE_GROUP}'].id | [0]" \
               --output tsv | tr -d '\r')

if [[ -z "${ID_GRUPO}" ]]; then
    ID_GRUPO=$(az pipelines variable-group create \
                   --name "${VARIABLE_GROUP}" \
                   --description "Segredos da aplicacao, copiados do Key Vault ${KEYVAULT_NAME}" \
                   --variables DB_JDBC_URL="${JDBC_URL}" \
                   --authorize true \
                   --query id --output tsv | tr -d '\r')
    echo "  grupo ........................ criado (id ${ID_GRUPO})"
else
    az pipelines variable-group variable update \
        --group-id "${ID_GRUPO}" \
        --name DB_JDBC_URL \
        --value "${JDBC_URL}" \
        --output none
    echo "  grupo ........................ ja existe (id ${ID_GRUPO})"
fi

# Grava (ou regrava) um segredo. O valor chega ao az pela variavel de
# ambiente AZURE_DEVOPS_EXT_PIPELINE_VAR_<NOME>, nunca pela linha de comando.
gravar_segredo() {
    local nome="$1"
    local valor="$2"

    az pipelines variable-group variable delete \
        --group-id "${ID_GRUPO}" --name "${nome}" --yes \
        --output none 2>/dev/null || true

    env "AZURE_DEVOPS_EXT_PIPELINE_VAR_${nome}=${valor}" \
        az pipelines variable-group variable create \
            --group-id "${ID_GRUPO}" \
            --name "${nome}" \
            --secret true \
            --output none
    echo "  ${nome} .......... gravado como segredo"
}

gravar_segredo DB_USER     "${APP_USER}"
gravar_segredo DB_PASSWORD "$(ler_segredo "${SECRET_APP_PASSWORD}")"
gravar_segredo JWT_SECRET  "$(ler_segredo "${SECRET_JWT}")"
gravar_segredo MASTER_PASSWORD "$(ler_segredo "${SECRET_MASTER}")"

titulo "RESULTADO"
az pipelines variable-group variable list --group-id "${ID_GRUPO}" --output table

cat <<EOF

PROXIMO PASSO - SERVICE CONNECTIONS (uma unica vez)

  1. GitHub - pelo portal, porque depende do OAuth do dono do repositorio
     Project Settings > Service connections > New service connection > GitHub
       Authentication : Grant authorization (OAuth "AzurePipelines")
       Name           : ${SC_GITHUB}
       Grant access permission to all pipelines: marcado
     Quem autoriza precisa ser admin de ${GITHUB_REPO_URL}. O repo e
     PESSOAL do Olavo: so ele e admin (repo pessoal nao tem papel Admin
     para colaborador). Numa janela anonima: Azure DevOps com a conta do
     RM561940 e GitHub com a do Olavo, que autoriza no popup. E o Azure
     DevOps que cria o webhook que dispara o CI a cada push.

  2. Azure Resource Manager - pelo CLI:
       ./06b_service-connection-azure.sh

Depois rode 07_pipelines.sh.
EOF
