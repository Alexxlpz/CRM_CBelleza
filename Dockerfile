# syntax=docker/dockerfile:1.7

# --- Build Stage ---
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Primero solo pom.xml y las dependencias: Docker guarda esta capa y, mientras pom.xml no cambie, cada
# reconstrucción se la salta (no vuelve a bajar todo de Maven Central) y solo compila el código.
# go-offline no baja los procesadores de anotaciones (Lombok, configuration-processor): se compila una clase vacía
# para que también queden en esta capa, y luego se borra.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline \
    && mkdir -p src/main/java && echo 'class Warmup {}' > src/main/java/Warmup.java \
    && mvn -B -q compile \
    && rm -rf src target

# Build production jar skipping tests
COPY src ./src
RUN mvn -B -DskipTests clean package

# --- Runtime Stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-root user for security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy executable jar from build stage
COPY --from=build /workspace/target/*.jar /app/app.jar

# Hora del servidor: las citas se guardan en hora local y se comparan con "ahora" (se puede cambiar con TZ en docker-compose)
ENV TZ=Europe/Madrid

EXPOSE 8080

# Memoria de la JVM: el 75 % de la que tenga el contenedor (por defecto Java solo usa un 25 %).
# Si se acaba, la app se reinicia (restart: unless-stopped) en vez de quedarse colgada.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]