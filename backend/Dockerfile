FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
# Dependencies first: this layer is reused while only the sources change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
# The tests need Docker (Testcontainers) and run in CI (`./mvnw verify`); the image only packages.
RUN ./mvnw -B -q package -DskipTests \
    && cp target/pulseboard-pos-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S -g 10001 pos && adduser -S -u 10001 -G pos pos
COPY --from=build /workspace/app.jar app.jar
USER 10001:10001
EXPOSE 8080
# All configuration (database, JWT secret, users, PulseBoard key) comes from environment variables.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
