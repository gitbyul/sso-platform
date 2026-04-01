FROM gradle:9.4.1-jdk25 AS builder
WORKDIR /workspace

COPY . .
RUN gradle :sso-bootstrap:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /workspace/sso-bootstrap/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]
