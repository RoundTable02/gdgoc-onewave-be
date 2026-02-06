# Spring Boot Application Dockerfile
# Uses pre-built JAR from Gradle build step

# -----------------------------------------------------------------------------
# Production stage
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy the pre-built JAR (passed as build arg)
ARG JAR_FILE
COPY ${JAR_FILE} app.jar

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
