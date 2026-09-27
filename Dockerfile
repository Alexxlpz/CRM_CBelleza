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

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]