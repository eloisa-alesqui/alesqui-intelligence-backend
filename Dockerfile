# syntax=docker/dockerfile:1

# ==========================
# Build stage
# ==========================
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

# Leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn -q -B -e -DskipTests dependency:go-offline

# Copy sources and build
COPY src ./src
RUN mvn -q -B -DskipTests clean package

# ==========================
# Runtime stage
# ==========================
FROM eclipse-temurin:21-jre

ENV APP_HOME=/opt/app \
    JAVA_OPTS=""

WORKDIR ${APP_HOME}

# Copy the built JAR
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar 2>/dev/null || true
COPY --from=build /app/target/*-*.jar app.jar

# Expose port (Render sets PORT env var; server.port is already wired to ${PORT:8080})
EXPOSE 8080

# Healthcheck (optional; Render also uses path-based health checks)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT:-8080}/actuator/health | grep -q '"status":"UP"' || exit 1

# Run the app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
