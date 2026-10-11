# syntax=docker/dockerfile:1

# Stage 1: build
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Si hay wrapper, usar ./mvnw; si no, instalar maven
RUN apt-get update && apt-get install -y maven curl && mvn -B package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl netcat-openbsd && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/*.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
