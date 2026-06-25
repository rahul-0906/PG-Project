# Stage 1: Build Stage
FROM maven:3.9-eclipse-temurin-23-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime Stage
FROM eclipse-temurin:23-jdk-alpine
# Install bash and docker-cli so ProcessBuilder can run provision_tenant.sh and control containers
RUN apk add --no-cache bash docker-cli

WORKDIR /app
# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar
# Copy scripts directory from build stage
COPY --from=build /app/src/main/resources/scripts/ /app/src/main/resources/scripts/

# Expose backend port
EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
