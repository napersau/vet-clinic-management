# syntax=docker/dockerfile:1.7
# Một Dockerfile dùng chung cho mọi module: docker build --build-arg MODULE=clinical-service .
# Build trong container (JDK 21) nên không phụ thuộc JDK cài trên máy.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Mạng build của Docker Desktop thường không có IPv6 -> JVM ưu tiên IPv6 sẽ gặp "Network is unreachable"
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true -Dmaven.wagon.http.retryHandler.count=3"
COPY . .
ARG MODULE
# sharing=locked: các service build song song không ghi đè cache ~/.m2 của nhau
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl ${MODULE} -am -DskipTests package \
    && cp ${MODULE}/target/${MODULE}.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
# curl dùng cho healthcheck /actuator/health trong docker-compose
RUN command -v curl >/dev/null 2>&1 || (apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*)
RUN groupadd --system vetclinic && useradd --system --gid vetclinic vetclinic \
    && mkdir -p /app/uploads/imaging && chown -R vetclinic:vetclinic /app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER vetclinic
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
