# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Run stage: just the JRE and the jar
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home hank
WORKDIR /app
COPY --from=build /src/target/BigHank.jar BigHank.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod 755 /app/entrypoint.sh && mkdir -p /app/data && chown hank:hank /app/data

# Player progress is saved in /app/data. Mount a persistent volume there (Railway: mount path /app/data).
# Keep memory use (and hosting cost) low; override JAVA_OPTS if you ever need more.
ENV JAVA_OPTS="-Xmx256m -XX:+UseSerialGC"

# The entrypoint fixes volume ownership, then drops from root to the unprivileged "hank" user.
# The token is passed in at runtime (DISCORD_TOKEN), never baked into the image.
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/BigHank.jar"]
