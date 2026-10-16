FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add curl for health check
RUN apk --no-cache add curl

ARG JAR_FILE=target/relay-messaging-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

ENV SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
