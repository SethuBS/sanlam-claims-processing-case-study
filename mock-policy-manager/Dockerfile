FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/mock-policy-manager-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
