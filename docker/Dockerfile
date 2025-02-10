# Use maven image for building
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

# Set working directory
WORKDIR /app

# Clone the Git repository
ARG GIT_REPO="https://github.com/bitsneak/DiscordSchoolBot"
RUN apk add --no-cache git && \
    git clone --depth=1 ${GIT_REPO} . && \
    cd bot && mvn clean package -DskipTests

# Use a minimal runtime
FROM eclipse-temurin:21-jre-alpine

# Set working directory in the runtime container
WORKDIR /app

# Copy the built JAR from the Maven container
COPY --from=builder /app/bot/target/*.jar app.jar

# Create and set the log and db directories as mountable points
VOLUME ["/app/log", "/app/db"]

# Expose port
EXPOSE 8080

# Start the application
CMD ["sh", "-c", "java -jar app.jar"]
