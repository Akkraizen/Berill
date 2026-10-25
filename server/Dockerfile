FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY gradlew ./gradlew
COPY gradle/ ./gradle/
COPY deploy/gradle/sync.settings.gradle.kts ./settings.gradle.kts
COPY deploy/gradle/protocol.build.gradle.kts ./protocol/build.gradle.kts
COPY server/build.gradle.kts ./server/build.gradle.kts
COPY version.properties ./version.properties
COPY protocol/src/ ./protocol/src/
COPY server/src/ ./server/src/

RUN sed -i 's/\r$//' gradlew \
    && sh ./gradlew --no-daemon :server:test :server:buildFatJar

FROM eclipse-temurin:21-jre AS runtime
ARG VERSION=dev
ARG REVISION=unknown
ARG SOURCE=https://github.com/Akkraizen/Berill
LABEL org.opencontainers.image.title="Berill sync server" \
      org.opencontainers.image.description="Berill synchronization server" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.source="${SOURCE}"
WORKDIR /app
COPY --from=builder /build/server/build/libs/berill-server.jar /app/berill-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/berill-server.jar"]
