# Builds the Quarkus LangChain4j agent registry sample (weather MCP server, A2A text agents,
# orchestrator) from a Quarkus LangChain4j release, against the released extensions.
# The first build takes a few minutes; later builds reuse the cached layers.
ARG QL4J_VERSION=1.15.0

FROM maven:3.9-eclipse-temurin-21 AS build
ARG QL4J_VERSION
WORKDIR /src
RUN git clone -q --depth 1 --branch "${QL4J_VERSION}" https://github.com/quarkiverse/quarkus-langchain4j.git .
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -f samples/agent-registry-workflow/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /apps
COPY --from=build /src/samples/agent-registry-workflow/weather-server/target/quarkus-app /apps/weather-server
COPY --from=build /src/samples/agent-registry-workflow/text-agent/target/quarkus-app /apps/text-agent
COPY --from=build /src/samples/agent-registry-workflow/orchestrator/target/quarkus-app /apps/orchestrator
# Inside the container every app listens on all interfaces; the compose file publishes only
# the dashboard, on localhost.
ENV QUARKUS_HTTP_HOST=0.0.0.0
ENTRYPOINT ["java", "-jar"]
