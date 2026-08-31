FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/claims-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
