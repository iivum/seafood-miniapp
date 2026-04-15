FROM eclipse-temurin:17-jre

ARG JAR_FILE=build/libs/product-service-1.0.0-SNAPSHOT.jar

COPY ${JAR_FILE} /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
