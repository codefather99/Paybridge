# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Copy only the pom first so Docker can cache the dependency download
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Stage 2: run it ----
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 appuser
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER appuser

# Memory-conscious JVM settings for a small container
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxMetaspaceSize=160m"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]