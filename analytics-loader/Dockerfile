# Built from the jar Maven leaves in target/ (mvn package first), not from source:
# the same jar the tests ran against is the one that ships.
#
#   docker build -t ghcr.io/ffetih/ff-logistic-shared/analytics-loader:local analytics-loader

# Split the jar into layers that change at different speeds: libraries rarely,
# the application classes on every commit. Only the last layer is rebuilt and pushed.
FROM eclipse-temurin:25-jre AS layers
WORKDIR /build
COPY target/ff-analytics-loader.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted \
 && mkdir -p extracted/dependencies extracted/spring-boot-loader \
             extracted/snapshot-dependencies extracted/application

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
# Size the heap from the container's memory limit, not the node's
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
USER 10001
EXPOSE 8086
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
