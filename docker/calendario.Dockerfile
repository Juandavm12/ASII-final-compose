FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY snapshots/API-Calendario-T2/ ./
RUN mvn -B -DskipTests package && cp target/*.jar /app.jar
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
