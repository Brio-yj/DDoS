FROM gradle:8.5-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN gradle :api-a:bootJar --no-daemon

FROM eclipse-temurin:17-jre
ENV JAVA_TOOL_OPTIONS="-Xms1g -Xmx1g"
WORKDIR /app
COPY --from=builder /workspace/api-a/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","app.jar"]
