#!/usr/bin/env bash
#
# 05 - Cria o App Service Plan e o Web App (Java 21 SE) que recebem o
#      deploy do CD.
#
# App Service sem container: o CD publica o JAR que o CI gerou. Este
# script so prepara o "terreno" e NAO grava segredo nenhum: a URL do banco,
# o usuario, a senha e o segredo do JWT sao aplicados pela pipeline de CD
# a cada deploy, a partir do variable group secreto do Azure DevOps. Ate o
# primeiro CD rodar, o Web App mostra a pagina padrao do App Service.
#
# Fuso horario: JAVA_OPTS=-Duser.timezone=America/Sao_Paulo. O App Service
# roda em UTC e a imagem Java nao traz tzdata (a variavel TZ e ignorada);
# sem isto o job de lembretes (cron das 8h) e os logs saem 3 horas a frente.
#
# Porta: o App Service Java SE informa a porta em PORT e o
# application.properties ja le server.port=${PORT:8080}.
#
# Tempo de subida: WEBSITES_CONTAINER_START_TIME_LIMIT=600. O padrao e
# 230 s, e no primeiro boot o Flyway aplica a cadeia V0 -> V14 inteira
# (37 tabelas e o motor em PL/SQL) num Oracle remoto, alem do start da JVM
# num B1. Passando do limite, o App Service mata a aplicacao e reinicia em
# loop.
#
# Projeto Clyvo Vet - Sprint 4 - RM561940
#
set -euo pipefail
source "$(dirname "$0")/00_variables.sh"

exigir_comando az

titulo "05 - WEB APP"
echo "Plano    : ${APP_SERVICE_PLAN} (Linux B1)"
echo "Web App  : ${WEBAPP_NAME}"
echo "Runtime  : ${WEBAPP_RUNTIME}"
echo "URL      : ${WEBAPP_URL}"

titulo "APP SERVICE PLAN"
if az appservice plan show --resource-group "${RESOURCE_GROUP}" \
                           --name "${APP_SERVICE_PLAN}" >/dev/null 2>&1; then
    echo "AVISO: o plano '${APP_SERVICE_PLAN}' ja existe. Nada a fazer."
else
    # B1: tem Always On e 1,75 GB. O F1 gratuito dorme, tem cota diaria de
    # CPU e 1 GB - pouco para o Spring Boot com Hibernate e Flyway.
    az appservice plan create \
        --resource-group "${RESOURCE_GROUP}" \
        --name "${APP_SERVICE_PLAN}" \
        --location "${LOCATION}" \
        --is-linux \
        --sku B1 \
        --tags "${TAGS[@]}" \
        --output none
    echo "  plano ........................ criado"
fi

titulo "WEB APP"
if az webapp show --resource-group "${RESOURCE_GROUP}" \
                  --name "${WEBAPP_NAME}" >/dev/null 2>&1; then
    echo "AVISO: o Web App '${WEBAPP_NAME}' ja existe. Nada a fazer."
else
    az webapp create \
        --resource-group "${RESOURCE_GROUP}" \
        --plan "${APP_SERVICE_PLAN}" \
        --name "${WEBAPP_NAME}" \
        --runtime "${WEBAPP_RUNTIME}" \
        --tags "${TAGS[@]}" \
        --output none
    echo "  web app ...................... criado"
fi

titulo "APP SETTINGS (somente as que nao sao segredo)"
az webapp config appsettings set \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${WEBAPP_NAME}" \
    --settings "JAVA_OPTS=-Duser.timezone=America/Sao_Paulo" \
               "WEBSITES_CONTAINER_START_TIME_LIMIT=600" \
    --output none
echo "  JAVA_OPTS .................... gravada"
echo "  WEBSITES_CONTAINER_START_TIME_LIMIT ... gravada"

titulo "CONFIGURACAO DO SITE"
az webapp config set \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${WEBAPP_NAME}" \
    --always-on true \
    --min-tls-version 1.2 \
    --ftps-state Disabled \
    --output none
az webapp update \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${WEBAPP_NAME}" \
    --https-only true \
    --output none
az webapp log config \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${WEBAPP_NAME}" \
    --docker-container-logging filesystem \
    --output none
echo "  Always On, HTTPS only, TLS 1.2, FTPS desligado, log de container"

titulo "RESULTADO"
az webapp show \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${WEBAPP_NAME}" \
    --query "{webapp:name, estado:state, runtime:siteConfig.linuxFxVersion, url:defaultHostName, httpsOnly:httpsOnly}" \
    --output table

echo ""
echo "PROXIMO PASSO: 06_azure-devops.sh (projeto, professor e variable group)."
