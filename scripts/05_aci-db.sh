#!/usr/bin/env bash
#
# 05 - Cria o ACI do BANCO DE DADOS (container group 1 de 2).
#
# Pontos que o enunciado cobra e que estao implementados aqui:
#   - imagem vinda do ACR (nunca do Docker Hub)
#   - banco containerizado (-40 se nao for)
#   - senhas por --secure-environment-variables: com
#     --environment-variables elas apareceriam em texto claro no
#     `az container show` e no Portal
#
# SEM VOLUME. O spike de 25-26/08 mostrou que o Oracle sobre Azure Files
# falha com ORA-01990 no primeiro boot (o password file vira symlink que o
# CIFS nao resolve), e a validacao local de 09/09 confirmou que sem mount
# o boot e limpo. O enunciado nao exige persistencia em disco - exige
# evidenciar o dado gravado via SELECT, o que o disco efemero atende.
#
# CONSEQUENCIA: recriar este container zera o banco. Nao reinicie o ACI
# do banco durante a gravacao do video.
#
# 2 vCPU / 4 GB: o Oracle XE aloca SGA de 1,6 GB e nao inicia com menos
# de 2 GB. Validado no spike.
#
# Projeto Clyvo Vet - Sprint 3 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "05 - ACI DO BANCO DE DADOS"
echo "ACI     : ${ACI_DB}"
echo "Imagem  : ${ACR_LOGIN_SERVER}/${IMAGE_DB}:${IMAGE_TAG}"
echo "Recursos: 2 vCPU / 4 GB"
echo "Volume  : nenhum (disco efemero)"
echo "FQDN    : ${DNS_DB}.${LOCATION}.azurecontainer.io"

if az container show --resource-group "${RESOURCE_GROUP}" \
                     --name "${ACI_DB}" >/dev/null 2>&1; then
    echo ""
    echo "AVISO: o ACI '${ACI_DB}' ja existe."
    echo "Para recria-lo, remova-o antes:"
    echo "    az container delete -g ${RESOURCE_GROUP} -n ${ACI_DB} --yes"
    echo "ATENCAO: recriar o banco apaga os dados (nao ha volume)."
    exit 0
fi

titulo "LEITURA DE CREDENCIAIS (runtime, nada em disco)"

ACR_USERNAME=$(az acr credential show --name "${ACR_NAME}" \
                                      --query username --output tsv)
ACR_PASSWORD=$(az acr credential show --name "${ACR_NAME}" \
                                      --query "passwords[0].value" --output tsv)
echo "  credencial do ACR ............ lida"

ORACLE_PASSWORD=$(ler_segredo "${SECRET_ORACLE_PASSWORD}")
APP_USER_PASSWORD=$(ler_segredo "${SECRET_APP_PASSWORD}")
echo "  senhas do Key Vault .......... lidas"

titulo "CRIACAO DO CONTAINER GROUP"

az container create \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${ACI_DB}" \
    --image "${ACR_LOGIN_SERVER}/${IMAGE_DB}:${IMAGE_TAG}" \
    --location "${LOCATION}" \
    --os-type Linux \
    --cpu 2 \
    --memory 4 \
    --registry-login-server "${ACR_LOGIN_SERVER}" \
    --registry-username "${ACR_USERNAME}" \
    --registry-password "${ACR_PASSWORD}" \
    --ports 1521 \
    --ip-address Public \
    --dns-name-label "${DNS_DB}" \
    --restart-policy Always \
    --environment-variables \
        APP_USER="${APP_USER}" \
        ORACLE_DATABASE="${ORACLE_DATABASE}" \
    --secure-environment-variables \
        ORACLE_PASSWORD="${ORACLE_PASSWORD}" \
        APP_USER_PASSWORD="${APP_USER_PASSWORD}" \
    --output table

# As variaveis com segredo saem da memoria do shell assim que o comando termina.
unset ACR_PASSWORD ORACLE_PASSWORD APP_USER_PASSWORD

titulo "AGUARDANDO O ORACLE FICAR PRONTO"
echo "Fazendo poll em 'az container logs' ate aparecer"
echo "'DATABASE IS READY TO USE!'. Na validacao local levou 134s."
echo "Timeout: 8 minutos."

PRONTO=0
for tentativa in $(seq 1 48); do
    LOG=$(az container logs --resource-group "${RESOURCE_GROUP}" \
                            --name "${ACI_DB}" 2>/dev/null || true)

    if echo "${LOG}" | grep -q "DATABASE IS READY TO USE"; then
        echo ""
        echo "Oracle pronto apos aproximadamente $((tentativa * 10)) segundos."
        PRONTO=1
        break
    fi

    # ORA-00845 = memoria insuficiente; ORA-01990 = password file (mount).
    # Falhar cedo evita esperar o timeout inteiro por um erro ja visivel.
    if echo "${LOG}" | grep -qE "ORA-00845|ORA-01990"; then
        echo ""
        echo "ERRO: o Oracle abortou com erro conhecido." >&2
        echo "${LOG}" | grep -E "ORA-[0-9]{5}" | head -5 >&2
        exit 1
    fi

    printf "."
    sleep 10
done

if [[ "${PRONTO}" -eq 0 ]]; then
    echo ""
    echo "ERRO: o Oracle nao ficou pronto em 8 minutos." >&2
    echo "Ultimas linhas do log:" >&2
    az container logs --resource-group "${RESOURCE_GROUP}" --name "${ACI_DB}" 2>&1 | tail -30 >&2
    exit 1
fi

titulo "RESULTADO"
az container show \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${ACI_DB}" \
    --query "{aci:name, estado:instanceView.state, fqdn:ipAddress.fqdn, ip:ipAddress.ip}" \
    --output table

echo ""
echo "FQDN do banco (usado pelo script 06):"
az container show --resource-group "${RESOURCE_GROUP}" \
                  --name "${ACI_DB}" \
                  --query ipAddress.fqdn --output tsv
