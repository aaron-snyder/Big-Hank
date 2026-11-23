# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package

# Run stage: just the JRE and the jar, running as an unprivileged user
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home hank
WORKDIR /app
COPY --from=build /src/target/BigHank.jar BigHank.jar
RUN mkdir data && chown hank:hank data
USER hank
# Player progress lives here; mount a volume so it survives restarts and redeploys.
VOLUME /app/data
# The token is passed in at runtime (docker run -e DISCORD_TOKEN=... or --env-file .env), never baked in.
ENTRYPOINT ["java", "-jar", "BigHank.jar"]
