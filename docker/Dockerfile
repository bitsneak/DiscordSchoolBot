# Use maven image for building
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

# Set working directory
WORKDIR /app

# Clone Git repository
ARG GIT_REPO="https://github.com/bitsneak/DiscordSchoolBot"
RUN apk add --no-cache git && \
    git clone --depth=1 ${GIT_REPO} . && \
    cd bot && mvn clean package -DskipTests

RUN cd bot && mvn clean package -DskipTests

# Use minimal runtime
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy built JAR from the Maven container
COPY --from=builder /app/bot/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Start application
CMD ["sh", "-c", "java -jar app.jar"]
