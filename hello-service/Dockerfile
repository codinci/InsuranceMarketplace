# Build the hello-service jar (and its common modules) in a Maven image, then run it on a JRE image.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common-events ./common-events
COPY common-starter ./common-starter
COPY hello-service ./hello-service
RUN mvn -B -q -pl hello-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
WORKDIR /app
COPY --from=build /workspace/hello-service/target/hello-service-*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_OPTS="-Xmx256m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
