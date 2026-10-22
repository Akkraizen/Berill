FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/tessera-server.jar /app/tessera-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/tessera-server.jar"]
