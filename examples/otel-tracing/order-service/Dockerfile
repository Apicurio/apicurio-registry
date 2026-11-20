# Dockerfile for Order Service
# Uses the Quarkus fast-jar deployment format

FROM registry.access.redhat.com/ubi10/openjdk-21-runtime:latest

ENV LANGUAGE='en_US:en'

# Copy the Quarkus application
COPY --chown=1001:0 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=1001:0 target/quarkus-app/*.jar /deployments/
COPY --chown=1001:0 target/quarkus-app/app/ /deployments/app/
COPY --chown=1001:0 target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 1001

ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

WORKDIR /deployments
CMD java $JAVA_OPTIONS -jar quarkus-run.jar
