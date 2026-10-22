#!/usr/bin/env bash
#
# 04 - Cria o ACI do BANCO DE DADOS (Oracle XE 21c em container).
#
#   - imagem vinda do ACR (03_acr.sh), nunca direto do Docker Hub
#   - senhas por --secure-environment-variables: com
#     --environment-variables elas apareceriam em texto claro no
#     `az container show` e no Portal
#   - FQDN publico na porta 1521: o Web App alcanca o banco por ele
#
# RISCO ACEITO - banco publico: o video exige mostrar cada operacao do CRUD
# com SELECT no banco a partir da maquina de quem grava, e o ACI nao aceita
# NSG/whitelist em IP publico. Mitigacoes: senhas de 28 caracteres
# aleatorias no Key Vault, usuario da app sem privilegio de DBA, e o ACI
# fica parado fora das sessoes (97_pausar.sh). Fora do contexto academico,
# o caminho e ACI em VNet com IP privado + integracao de VNet no Web App.
#
# SEM VOLUME. Na Sprint 3 o Oracle sobre Azure Files falhou com ORA-01990
# no primeiro boot (o password file vira symlink que o CIFS nao resolve) e
# sem mount o boot e limpo. Ver docs/VALIDACAO_LOCAL.md.
#
# CONSEQUENCIA: recriar este container zera o banco. Nao reinicie o ACI
# do banco durante a gravacao do video.
#
# 2 vCPU / 4 GB: o Oracle XE aloca SGA de 1,6 GB e nao inicia com menos
# de 2 GB.
#
# O schema NAO e criado aqui: o Flyway da aplicacao aplica as migrations
# no primeiro boot depois do deploy feito pelo CD.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "04 - ACI DO BANCO DE DADOS"
echo "ACI     : ${ACI_DB}"
echo "Imagem  : ${ACR_LOGIN_SERVER}/${IMAGE_DB}:${IMAGE_TAG}"
echo "Recursos: 2 vCPU / 4 GB"
echo "Volume  : nenhum (disco efemero)"
echo "FQDN    : ${DB_FQDN}"

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
                                      --query username --output tsv | tr -d '\r')
ACR_PASSWORD=$(az acr credential show --name "${ACR_NAME}" \
                                      --query "passwords[0].value" --output tsv | tr -d '\r')
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
echo "'DATABASE IS READY TO USE!'. Na Sprint 3 levou cerca de 2 minutos."
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
echo "URL JDBC que o CD grava no Web App:"
echo "  ${JDBC_URL}"
