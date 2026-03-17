# Dockerfile for running the pre-built Quarkus consumer application
# NOTE: Run 'mvn clean package -DskipTests' in this directory before building the image

FROM registry.access.redhat.com/ubi10/ubi-minimal:latest

RUN microdnf install -y java-21-openjdk-headless && microdnf clean all

ENV LANGUAGE='en_US:en'

# Copy the pre-built Quarkus application
COPY --chown=1001:0 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=1001:0 target/quarkus-app/*.jar /deployments/
COPY --chown=1001:0 target/quarkus-app/app/ /deployments/app/
COPY --chown=1001:0 target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 1001

ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

WORKDIR /deployments
CMD java $JAVA_OPTIONS -jar quarkus-run.jar
