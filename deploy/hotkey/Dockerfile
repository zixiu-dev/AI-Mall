FROM maven:3.9.9-eclipse-temurin-17 AS build
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY scripts/build-hotkey.sh scripts/build-hotkey.sh
COPY deploy/hotkey/java17-worker.patch deploy/hotkey/java17-worker.patch
RUN bash scripts/build-hotkey.sh

FROM eclipse-temurin:17-jre
COPY --from=build /build/.runtime/hotkey-worker.jar /app/worker.jar
EXPOSE 11111 18081
ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-jar", "/app/worker.jar"]
