############################
# Builder stage
############################
FROM gradle:8-jdk17 AS builder

# Set working directory
WORKDIR /home/gradle/project

# Copy Gradle wrapper and source
COPY --chown=gradle:gradle . .

# Use the Gradle wrapper to build the app
RUN ./gradlew clean bootJar --no-daemon

############################
# Runtime stage
############################
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built fat JAR from the builder stage
COPY --from=builder /home/gradle/project/build/libs/*.jar app.jar

# Expose port if your bot listens (optional, only for webhook/server)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
