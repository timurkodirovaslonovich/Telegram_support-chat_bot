# 1️⃣ Builder stage — compile the app
FROM gradle:8.0-jdk17 AS build

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Cache dependencies and build
RUN gradle clean build --no-daemon

# 2️⃣ Runtime stage — run the compiled JAR
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar from the builder
COPY --from=build /app/build/libs/*.jar app.jar

# Expose a port (optional – your bot probably doesn’t expose a web server)
# If your app opens a webhook server, change this accordingly
EXPOSE 8080

# Run the JAR
ENTRYPOINT ["java", "-jar", "app.jar"]
