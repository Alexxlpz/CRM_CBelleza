# syntax=docker/dockerfile:1.7
# API de productos de Cuquora: se construye y arranca desde el docker-compose.yml de la carpeta de arriba.

# --- Build Stage ---
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests clean package

# --- Runtime Stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=build /workspace/target/*.jar /app/app.jar
ENV TZ=Europe/Madrid
EXPOSE 8081
# Es pequeña: con 256 MB le sobra (el límite se pone en docker-compose.yml)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
