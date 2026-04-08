FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY ../pom.xml ../pom.xml
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spendwise && adduser -S spendwise -G spendwise
USER spendwise
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:+UseVirtualThreads", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
