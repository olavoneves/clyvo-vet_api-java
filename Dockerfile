# ============================================================
#  CLYVO VET - Dockerfile (API Java Spring Boot)
#  Multi-stage build: Maven (build) + JRE (execucao)
# ============================================================

# --- Estagio 1: Build com Maven ---
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copiar pom.xml e baixar dependencias (cache eficiente)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiar codigo-fonte e compilar
COPY src ./src
RUN mvn clean package -DskipTests


# --- Estagio 2: Imagem enxuta para execucao ---
FROM eclipse-temurin:21-jre

# Criar usuario nao-root
RUN groupadd --system appuser && useradd --system --gid appuser appuser

WORKDIR /app

# Copiar JAR do estagio de build
COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appuser app.jar

# Trocar para usuario nao-root
USER appuser

# Porta da aplicacao
EXPOSE 8080

# Iniciar aplicacao
ENTRYPOINT ["java", "-jar", "app.jar"]
