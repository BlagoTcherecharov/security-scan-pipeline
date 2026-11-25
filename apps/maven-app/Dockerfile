# Self-contained: builds the jar inside the image (no tests).
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21.0.1_12-jre-jammy
# Ubuntu image: groupadd/useradd (addgroup -S / adduser -S are Alpine-only)
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app
COPY --from=build /src/target/app.jar app.jar

USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/ || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
