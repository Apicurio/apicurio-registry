# Builds the Quarkus LangChain4j agent registry sample (weather MCP server, A2A text agents,
# orchestrator) from a pinned quarkus-langchain4j commit.
#
# Pinned because the released extensions (2.0.0.Beta2) predate MCP_SERVER discovery and
# paging through all agent cards, which the sample needs. Move to a release that has them.
# The first build takes several minutes; later builds reuse the cached layers.
ARG QL4J_REF=63d653054647a9f8c36193796727287ab243e343

FROM maven:3.9-eclipse-temurin-21 AS build
ARG QL4J_REF
WORKDIR /src
RUN git init -q . \
 && git remote add origin https://github.com/quarkiverse/quarkus-langchain4j.git \
 && git fetch -q --depth 1 origin "${QL4J_REF}" \
 && git checkout -q FETCH_HEAD
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl agentic/deployment,a2a-apicurio-registry/deployment,mcp-apicurio-registry/deployment,model-providers/ollama/deployment \
        -am install -DskipTests -Dno-format \
 && ./mvnw -B -q -f samples/agent-registry-workflow/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /apps
COPY --from=build /src/samples/agent-registry-workflow/weather-server/target/quarkus-app /apps/weather-server
COPY --from=build /src/samples/agent-registry-workflow/text-agent/target/quarkus-app /apps/text-agent
COPY --from=build /src/samples/agent-registry-workflow/orchestrator/target/quarkus-app /apps/orchestrator
# Inside the container every app listens on all interfaces; the compose file publishes only
# the dashboard, on localhost.
ENV QUARKUS_HTTP_HOST=0.0.0.0
ENTRYPOINT ["java", "-jar"]
