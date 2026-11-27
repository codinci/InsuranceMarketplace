# Build the policy-service jar (and the common modules it needs) in a Maven image, then run it on a JRE image.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common-events ./common-events
COPY common-starter ./common-starter
COPY common-messaging ./common-messaging
COPY policy-service ./policy-service
# The reactor lists every module, so the other modules must exist even though they are not built here.
COPY auth-service ./auth-service
COPY api-gateway ./api-gateway
COPY payment-service ./payment-service
COPY claim-service ./claim-service
COPY notification-service ./notification-service
COPY infra-tests ./infra-tests
RUN mvn -B -q -pl policy-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
WORKDIR /app
COPY --from=build /workspace/policy-service/target/policy-service-*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_OPTS="-Xmx256m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
