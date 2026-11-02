FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/vegam-traffic.jar ./vegam-traffic.jar
COPY web ./web
EXPOSE 8080
CMD ["java", "-jar", "vegam-traffic.jar", "--web"]
