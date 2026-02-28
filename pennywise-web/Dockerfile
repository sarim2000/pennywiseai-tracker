# Build stage
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

# Copy parser-core dependency (should be in .parser-core subdirectory)
COPY .parser-core/build.gradle.kts /app/parser-core/build.gradle.kts
COPY .parser-core/settings.gradle.kts /app/parser-core/settings.gradle.kts
COPY .parser-core/src /app/parser-core/src

# Build and publish parser-core to local Maven
WORKDIR /app/parser-core
ENV GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=512m"
RUN gradle publishToMavenLocal --no-daemon

# Copy server gradle files
WORKDIR /app/server
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradlew gradlew.bat ./
COPY server/build.gradle.kts ./server/

# Copy server source code
COPY server/src ./server/src

# Build the shadow JAR (uber JAR with all dependencies)
RUN ./gradlew :server:shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the fat JAR from build stage
COPY --from=build /app/server/server/build/libs/server-all.jar ./app.jar

# Expose port
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "app.jar"]
