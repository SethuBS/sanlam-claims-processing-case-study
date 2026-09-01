FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/mock-payment-system-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
