FROM eclipse-temurin:21-jdk-alpine AS builder

ARG MAVEN_VERSION=3.9.15
ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

WORKDIR /app

RUN wget -q https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && tar -xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt \
    && mv /opt/apache-maven-${MAVEN_VERSION} ${MAVEN_HOME} \
    && rm apache-maven-${MAVEN_VERSION}-bin.tar.gz

COPY pom.xml ../pom.xml
COPY notification-service/pom.xml .
COPY notification-service/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spendwise && adduser -S spendwise -G spendwise
USER spendwise
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
