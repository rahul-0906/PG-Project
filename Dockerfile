# ── Stage 1: Build the Spring Boot backend ──────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS backend-build
WORKDIR /app/backend
COPY backend/pom.xml .
COPY backend/src ./src
# Use the bundled Maven wrapper if available, else system maven
COPY apache-maven-3.9.6 /opt/maven
ENV PATH="/opt/maven/bin:$PATH"
RUN mvn -q -DskipTests clean package

# ── Stage 2: Build the React frontend ────────────────────────────────────────
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# ── Stage 3: Final runtime image ──────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the compiled backend jar
COPY --from=backend-build /app/backend/target/*.jar app.jar

# Copy the React build output into a static folder served by Spring Boot
COPY --from=frontend-build /app/frontend/dist /app/static

# Allow the tenant-config.yml to be overridden by a Docker volume mount
# Default is baked in; override at runtime with: -v /host/tenant-config.yml:/app/config/tenant-config.yml
COPY backend/src/main/resources/tenant-config.yml /app/config/tenant-config.yml

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Dspring.config.additional-location=file:/app/config/tenant-config.yml", \
  "-jar", "app.jar"]
