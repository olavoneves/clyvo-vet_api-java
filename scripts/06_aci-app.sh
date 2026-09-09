#!/usr/bin/env bash
#
# 06 - Cria o ACI da APLICACAO (container group 2 de 2).
#
# Sao dois ACIs SEPARADOS, nao um container group multi-container. Como os
# dois grupos nao compartilham rede interna, a aplicacao alcanca o banco
# pelo FQDN PUBLICO do ACI do banco - descoberto aqui em runtime.
#
# Este script nao contem nenhum endereco de loopback: entrega em localhost
# e zero de nota.
#
# O container do App roda como appuser (uid 999), nao root - requisito 8.2
# do enunciado (-10 se rodar como root). Quem garante isso e o USER do
# Dockerfile; aqui so nao se faz nada que desfaca.
#
# Projeto Clyvo Vet - Sprint 3 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "06 - ACI DA APLICACAO"
echo "ACI    : ${ACI_APP}"
echo "Imagem : ${ACR_LOGIN_SERVER}/${IMAGE_APP}:${IMAGE_TAG}"

if az container show --resource-group "${RESOURCE_GROUP}" \
                     --name "${ACI_APP}" >/dev/null 2>&1; then
    echo ""
    echo "AVISO: o ACI '${ACI_APP}' ja existe."
    echo "Para recria-lo, remova-o antes:"
    echo "    az container delete -g ${RESOURCE_GROUP} -n ${ACI_APP} --yes"
    exit 0
fi

titulo "DESCOBERTA DO FQDN DO BANCO"

DB_FQDN=$(az container show \
              --resource-group "${RESOURCE_GROUP}" \
              --name "${ACI_DB}" \
              --query ipAddress.fqdn --output tsv)

if [[ -z "${DB_FQDN}" ]]; then
    echo "ERRO: nao foi possivel obter o FQDN do ACI '${ACI_DB}'." >&2
    echo "Execute 05_aci-db.sh antes deste script." >&2
    exit 1
fi
echo "  ${DB_FQDN}"

# Formato thin com barra dupla e nome de SERVICO (nao SID). O PDB e o
# criado pelo entrypoint a partir de ORACLE_DATABASE. Apontar para XE
# (o CDB) devolve ORA-12514.
JDBC_URL="jdbc:oracle:thin:@//${DB_FQDN}:1521/${ORACLE_DATABASE}"
echo ""
echo "URL JDBC:"
echo "  ${JDBC_URL}"

titulo "LEITURA DE CREDENCIAIS (runtime, nada em disco)"

ACR_USERNAME=$(az acr credential show --name "${ACR_NAME}" \
                                      --query username --output tsv)
ACR_PASSWORD=$(az acr credential show --name "${ACR_NAME}" \
                                      --query "passwords[0].value" --output tsv)
echo "  credencial do ACR ...... lida"

APP_USER_PASSWORD=$(ler_segredo "${SECRET_APP_PASSWORD}")
JWT_SECRET=$(ler_segredo "${SECRET_JWT}")
echo "  segredos do Key Vault .. lidos"

titulo "CRIACAO DO CONTAINER GROUP"

# Sao DUAS variaveis secretas: a senha do banco e o segredo de assinatura
# do JWT. Ambas obrigatorias - a aplicacao nao sobe sem elas, porque o
# application.properties nao tem default embutido (o que seria segredo em
# codigo, -20).
az container create \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${ACI_APP}" \
    --image "${ACR_LOGIN_SERVER}/${IMAGE_APP}:${IMAGE_TAG}" \
    --location "${LOCATION}" \
    --os-type Linux \
    --cpu 1 \
    --memory 2 \
    --registry-login-server "${ACR_LOGIN_SERVER}" \
    --registry-username "${ACR_USERNAME}" \
    --registry-password "${ACR_PASSWORD}" \
    --ports 8080 \
    --ip-address Public \
    --dns-name-label "${DNS_APP}" \
    --restart-policy Always \
    --environment-variables \
        PORT=8080 \
        SPRING_DATASOURCE_URL="${JDBC_URL}" \
        SPRING_DATASOURCE_USERNAME="${APP_USER}" \
    --secure-environment-variables \
        SPRING_DATASOURCE_PASSWORD="${APP_USER_PASSWORD}" \
        JWT_SECRET="${JWT_SECRET}" \
    --output table

unset ACR_PASSWORD APP_USER_PASSWORD JWT_SECRET

titulo "AGUARDANDO A APLICACAO SUBIR"
echo "No primeiro boot o Flyway aplica 15 migrations (V0 ate V13) e cria"
echo "as 37 tabelas - por isso demora mais que um restart."
echo "E normal a aplicacao reiniciar 1 ou 2 vezes se o banco ainda estiver"
echo "aceitando as primeiras conexoes: a restart-policy e Always."
echo "Timeout: 8 minutos."

PRONTO=0
for tentativa in $(seq 1 48); do
    LOG=$(az container logs --resource-group "${RESOURCE_GROUP}" \
                            --name "${ACI_APP}" 2>/dev/null || true)

    if echo "${LOG}" | grep -q "Started ServerApplication"; then
        echo ""
        echo "Aplicacao pronta apos aproximadamente $((tentativa * 10)) segundos."
        PRONTO=1
        break
    fi

    if echo "${LOG}" | grep -q "APPLICATION FAILED TO START"; then
        echo ""
        echo "ERRO: a aplicacao abortou no startup." >&2
        echo "${LOG}" | tail -40 >&2
        exit 1
    fi

    printf "."
    sleep 10
done

if [[ "${PRONTO}" -eq 0 ]]; then
    echo ""
    echo "ERRO: a aplicacao nao subiu em 8 minutos." >&2
    echo "Ultimas linhas do log:" >&2
    az container logs --resource-group "${RESOURCE_GROUP}" --name "${ACI_APP}" 2>&1 | tail -40 >&2
    exit 1
fi

titulo "RESULTADO"
az container show \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${ACI_APP}" \
    --query "{aci:name, estado:instanceView.state, fqdn:ipAddress.fqdn}" \
    --output table

APP_FQDN=$(az container show --resource-group "${RESOURCE_GROUP}" \
                             --name "${ACI_APP}" \
                             --query ipAddress.fqdn --output tsv)
echo ""
echo "API disponivel em:"
echo "  http://${APP_FQDN}:8080/swagger-ui.html"
echo "  http://${APP_FQDN}:8080/actuator/health"
echo ""
echo "PROXIMO PASSO OBRIGATORIO: o banco nasce com o schema completo e"
echo "SEM dados. Rode 07_bootstrap.sh para criar a clinica e o usuario"
echo "master antes de qualquer teste."
