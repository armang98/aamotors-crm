# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- run ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app && mkdir -p /app/uploads && chown -R app:app /app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app
ENV MEDIA_ROOT=/app/uploads
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q UP <&3' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
