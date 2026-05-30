# syntax=docker/dockerfile:1

### Build stage ###
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Cache dependencies first
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Build the application
COPY src ./src
RUN ./gradlew bootJar --no-daemon

### Runtime stage ###
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8888

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
