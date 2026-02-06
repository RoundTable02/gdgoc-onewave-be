# Spring Boot Application Dockerfile
# Multi-stage build for optimized production image

# -----------------------------------------------------------------------------
# Build stage
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy gradle files for dependency caching
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
COPY src/ src/

# Build the application
RUN ./gradlew bootJar --no-daemon

# -----------------------------------------------------------------------------
# Production stage
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Set ownership to non-root user
RUN chown -R spring:spring /app

USER spring:spring

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || \
      wget --no-verbose --tries=1 --spider http://localhost:8080/health || \
      exit 1

# Run the application
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
