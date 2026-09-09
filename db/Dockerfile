# =====================================================================
# Clyvo Vet - Imagem do Banco de Dados (Oracle XE 21c)
# FIAP - DevOps Tools & Cloud Computing - Sprint 3
#
# O enunciado exige containerizacao completa: App e Banco (-40 se o
# banco nao for containerizado). Apontar o ACI direto para
# gvenzl/oracle-xe nao cumpre o requisito - por isso esta imagem propria.
#
# NENHUMA senha em ENV: as credenciais chegam por variavel de ambiente
# do ACI (--secure-environment-variables), lidas do Key Vault.
#
# Sem init.sql: o schema pertence as migrations do Flyway, aplicadas
# pela aplicacao no primeiro boot.
# =====================================================================
FROM gvenzl/oracle-xe:21-slim

EXPOSE 1521
