# syntax=docker/dockerfile:1.7

# --- Build Stage ---
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy dependencies descriptor and source code
COPY pom.xml .
COPY src ./src

# Build production jar skipping tests
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