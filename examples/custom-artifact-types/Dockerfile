# Apicurio Registry + the custom MARKDOWN artifact type.
#
# Derives from the re-augmentable ("-mutable") registry image: the provider jar is copied into
# the providers directory and /deployments/build.sh re-augments the application so that the
# classes become loadable. --prune drops lib/deployment afterwards (same size as the standard image).
#
# Build the jar first:  mvn package
ARG REGISTRY_IMAGE=apicurio/apicurio-registry:latest-snapshot-mutable
FROM ${REGISTRY_IMAGE}

COPY --chown=1001:0 target/custom-artifact-types.jar /deployments/quarkus-app/providers/
COPY --chown=1001:0 artifact-types.json /deployments/artifact-types.json

RUN /deployments/build.sh --prune

ENV APICURIO_ARTIFACT_TYPES_CONFIG_FILE=/deployments/artifact-types.json
