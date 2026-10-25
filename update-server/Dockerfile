FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY gradlew ./gradlew
COPY gradle/ ./gradle/
COPY deploy/gradle/updates.settings.gradle.kts ./settings.gradle.kts
COPY deploy/gradle/protocol.build.gradle.kts ./protocol/build.gradle.kts
COPY update-server/build.gradle.kts ./update-server/build.gradle.kts
COPY version.properties ./version.properties
COPY protocol/src/ ./protocol/src/
COPY update-server/src/ ./update-server/src/

RUN sed -i 's/\r$//' gradlew \
    && sh ./gradlew --no-daemon :update-server:test :update-server:buildFatJar

FROM eclipse-temurin:21-jre AS runtime
ARG VERSION=dev
ARG REVISION=unknown
ARG SOURCE=https://github.com/Akkraizen/Berill
LABEL org.opencontainers.image.title="Berill update server" \
      org.opencontainers.image.description="Berill application update server" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.source="${SOURCE}"
WORKDIR /app
COPY --from=builder /build/update-server/build/libs/berill-updates.jar /app/berill-updates.jar
RUN mkdir -p /app/releases
ENV RELEASES_DIR=/app/releases
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/berill-updates.jar"]
