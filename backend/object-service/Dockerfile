FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
# curl é usado apenas pelo HEALTHCHECK do Compose.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --no-create-home app
# Diretório de arquivos: um volume nomeado montado aqui herda este dono na primeira criação.
RUN mkdir -p /data/objects && chown -R app:app /data
WORKDIR /app
COPY --from=build /build/target/object-service.jar /app/app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
