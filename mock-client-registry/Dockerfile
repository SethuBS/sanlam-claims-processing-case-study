FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/mock-client-registry-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
