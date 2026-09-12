ARG DOCKER_REGISTRY

FROM ${DOCKER_REGISTRY}/maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -DskipTests

COPY src ./src
RUN mvn package -DskipTests

FROM ${DOCKER_REGISTRY}/eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/library-desk-chair-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]