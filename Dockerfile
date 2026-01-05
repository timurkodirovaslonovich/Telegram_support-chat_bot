############################
# Builder stage
############################
FROM gradle:8-jdk17 AS builder

WORKDIR /home/gradle/project

COPY . .

# Give gradlew execution permission
RUN chmod +x ./gradlew

# Build the project
RUN ./gradlew clean bootJar --no-daemon

############################
# Runtime stage
############################
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /home/gradle/project/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
