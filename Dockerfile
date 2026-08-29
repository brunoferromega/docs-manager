# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the build definition first so dependency resolution is cached independently of source edits.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null

COPY src/ src/
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Never run the API as root.
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
