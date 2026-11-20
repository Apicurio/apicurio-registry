# Builds the Quarkus LangChain4j agent registry sample (weather MCP server, A2A text agents,
# orchestrator) from Quarkus LangChain4j releases, against the released extensions.
# The first build takes a few minutes; later builds reuse the cached layers.
#
# The text agents and the orchestrator use the 2.x line (Quarkus 4). The weather MCP server is
# built from the 1.x line (Quarkus 3), because quarkus-mcp-server does not support Quarkus 4 yet
# (quarkiverse/quarkus-mcp-server#1149); the 2.x sample leaves it out of its build. It is a
# separate process that the other apps reach over MCP, so the two lines can differ.
ARG QL4J_VERSION=2.0.0.Beta4
ARG QL4J_MCP_SERVER_VERSION=1.15.1

FROM maven:3.9-eclipse-temurin-21 AS build
ARG QL4J_VERSION
WORKDIR /src
RUN git clone -q --depth 1 --branch "${QL4J_VERSION}" https://github.com/quarkiverse/quarkus-langchain4j.git .
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -f samples/agent-registry-workflow/pom.xml package -DskipTests -pl text-agent,orchestrator

FROM maven:3.9-eclipse-temurin-21 AS build-mcp-server
ARG QL4J_MCP_SERVER_VERSION
WORKDIR /src
RUN git clone -q --depth 1 --branch "${QL4J_MCP_SERVER_VERSION}" https://github.com/quarkiverse/quarkus-langchain4j.git .
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -f samples/agent-registry-workflow/pom.xml package -DskipTests -pl weather-server

FROM eclipse-temurin:21-jre
WORKDIR /apps
COPY --from=build-mcp-server /src/samples/agent-registry-workflow/weather-server/target/quarkus-app /apps/weather-server
COPY --from=build /src/samples/agent-registry-workflow/text-agent/target/quarkus-app /apps/text-agent
COPY --from=build /src/samples/agent-registry-workflow/orchestrator/target/quarkus-app /apps/orchestrator
# Inside the container every app listens on all interfaces; the compose file publishes only
# the dashboard, on localhost.
ENV QUARKUS_HTTP_HOST=0.0.0.0
ENTRYPOINT ["java", "-jar"]
