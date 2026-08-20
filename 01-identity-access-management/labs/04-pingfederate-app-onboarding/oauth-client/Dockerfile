# One image, both halves: the Java backend and the static frontend it serves.
# Stage 1 builds the jar; stage 2 keeps only the JRE and the jar, so the image stays small.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM alone first so Docker can cache the dependency download layer.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- run ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Never run a web app as root, not even in a lab.
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /build/target/*.jar /app/app.jar
RUN chown -R spring:spring /app
USER spring

# Render injects PORT; 8080 is the local default.
ENV PORT=8080
EXPOSE 8080

# Container-aware heap sizing, so the JVM respects Render's memory limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
