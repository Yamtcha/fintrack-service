FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml

COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build
COPY src ./src
RUN mvn package -DskipTests -q && \
    mv target/fintrack-api-*.jar target/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S fintrack && adduser -S fintrack -G fintrack
WORKDIR /app
COPY --from=builder /app/target/app.jar app.jar
USER fintrack
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
