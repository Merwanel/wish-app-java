# Default image for `docker compose up --build` (multi-stage; no local Maven required).
# For faster iteration with a prebuilt jar, see Dockerfile.local.
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy pom.xml and download dependencies first (better caching)
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy source code
COPY src/ src/

# Build JAR (same as root `npm run build:api-java`; -B + clean suited to CI/Docker layers)
RUN mvn -B clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S -G appgroup appuser

# Copy JAR from build stage
COPY --from=build /build/target/api-java-0.0.1-SNAPSHOT.jar app.jar

# Switch to non-root user
USER appuser

EXPOSE 3000

ENTRYPOINT ["java", "-jar", "app.jar"]
