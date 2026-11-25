# Self-contained: builds the jar inside the image (no tests).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.lockfile ./
COPY gradle ./gradle
COPY src ./src
RUN ./gradlew assemble --no-daemon -q

FROM eclipse-temurin:21.0.1_12-jre-jammy
# Ubuntu image: groupadd/useradd (addgroup -S / adduser -S are Alpine-only)
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app
COPY --from=build /src/build/libs/app.jar app.jar

USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/ || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
