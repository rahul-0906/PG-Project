# ─── Stage 1: Build ─────────────────────────────────────────────────────────
FROM maven:3.9.16-eclipse-temurin-23-alpine AS build
WORKDIR /app
# Cache dependencies first (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -q
# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:23-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# External tenant-config can be mounted at /config/tenant-config.yml
# Spring will auto-import it if SPRING_CONFIG_IMPORT points there
ENV SPRING_CONFIG_ADDITIONAL_LOCATION=file:/config/

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
