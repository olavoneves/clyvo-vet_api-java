#!/usr/bin/env bash
#
# 02 - Cria o Azure Key Vault e gera os segredos da solucao.
#
# Sao QUATRO segredos:
#   - senha do SYS do Oracle          (usada so pelo ACI do banco)
#   - senha do usuario petflow        (banco e aplicacao)
#   - segredo de assinatura do JWT    (aplicacao)
#   - senha do colaborador master     (aplicacao, bootstrap e testes)
#
# As senhas sao geradas AQUI, em tempo de execucao, com `openssl rand`.
# Nenhum valor literal existe em arquivo. O Key Vault e a fonte da verdade:
# o 04 le os segredos do banco e o 06 copia os da aplicacao para o variable
# group secreto do Azure DevOps, de onde o CD os grava no Web App.
#
# ATENCAO - soft delete: o Key Vault permanece "excluido reversivelmente"
# por 90 dias apos um `az group delete`. Recriar um cofre com o mesmo nome
# falha ate que ele seja purgado (az keyvault purge), o que e irreversivel.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az
exigir_comando openssl

titulo "02 - AZURE KEY VAULT"
echo "Cofre : ${KEYVAULT_NAME}"

# Alfanumerico puro: '/', '+' e '=' quebram a URL JDBC e o sqlplus na
# linha de comando.
gerar_senha() {
    openssl rand -base64 32 | tr -d '/+=\n' | head -c 28
}

# O JJWT com HS256 exige chave de no minimo 32 bytes.
gerar_jwt_secret() {
    openssl rand -base64 64 | tr -d '/+=\n' | head -c 48
}

if az keyvault show --name "${KEYVAULT_NAME}" \
                    --resource-group "${RESOURCE_GROUP}" >/dev/null 2>&1; then
    echo ""
    echo "AVISO: o cofre '${KEYVAULT_NAME}' ja existe. Nada a fazer."
else
    # Verifica se o nome esta preso em soft delete antes de tentar criar.
    if az keyvault list-deleted --query "[?name=='${KEYVAULT_NAME}'].name" \
                                --output tsv 2>/dev/null | grep -q .; then
        echo ""
        echo "ERRO: existe um cofre '${KEYVAULT_NAME}' excluido reversivelmente." >&2
        echo "Para reutilizar o nome e preciso purga-lo:" >&2
        echo "    az keyvault purge --name ${KEYVAULT_NAME}" >&2
        echo "Isso e IRREVERSIVEL. Confirme com o responsavel antes." >&2
        exit 1
    fi

    az keyvault create \
        --resource-group "${RESOURCE_GROUP}" \
        --name "${KEYVAULT_NAME}" \
        --location "${LOCATION}" \
        --enable-rbac-authorization false \
        --tags "${TAGS[@]}" \
        --output table
fi

titulo "SEGREDOS"

# Cada segredo so e criado se ainda nao existir: reexecutar o script nao
# troca a senha de um banco que ja esta no ar.
criar_segredo_se_ausente() {
    local nome="$1"
    local valor_fn="$2"
    if az keyvault secret show --vault-name "${KEYVAULT_NAME}" \
                               --name "${nome}" >/dev/null 2>&1; then
        echo "  ${nome} .......... ja existe (mantido)"
    else
        az keyvault secret set \
            --vault-name "${KEYVAULT_NAME}" \
            --name "${nome}" \
            --value "$(${valor_fn})" \
            --output none
        echo "  ${nome} .......... criado"
    fi
}

criar_segredo_se_ausente "${SECRET_ORACLE_PASSWORD}" gerar_senha
criar_segredo_se_ausente "${SECRET_APP_PASSWORD}"    gerar_senha
criar_segredo_se_ausente "${SECRET_JWT}"             gerar_jwt_secret
criar_segredo_se_ausente "${SECRET_MASTER}"          gerar_senha

titulo "RESULTADO"
az keyvault secret list \
    --vault-name "${KEYVAULT_NAME}" \
    --query "[].{segredo:name, habilitado:attributes.enabled}" \
    --output table

echo ""
echo "Os VALORES dos segredos nunca sao impressos nem gravados em disco."
