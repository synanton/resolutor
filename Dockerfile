# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY buildSrc buildSrc
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY resolutor-domain resolutor-domain
COPY resolutor-application resolutor-application
COPY resolutor-adapter-persistence-jpa resolutor-adapter-persistence-jpa
COPY resolutor-adapter-web resolutor-adapter-web
COPY resolutor-adapter-resource-http resolutor-adapter-resource-http
COPY resolutor-adapter-kafka resolutor-adapter-kafka
COPY resolutor-adapter-metrics resolutor-adapter-metrics
COPY resolutor-app resolutor-app
RUN chmod +x gradlew \
    && ./gradlew --no-daemon :resolutor-app:bootJar -x test \
    && cp resolutor-app/build/libs/resolutor-app.jar /src/app.jar

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /src/app.jar /app/resolutor.jar
EXPOSE 8080
USER nonroot
CMD ["-jar", "/app/resolutor.jar"]
