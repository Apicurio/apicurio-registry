FROM registry.access.redhat.com/ubi8/ubi-minimal:8.4-210 as builder

ARG VERSION=2.1.4-SNAPSHOT

RUN microdnf install wget tar make java-11-openjdk-devel-11.0.13.0.8-3.el8_5.x86_64 git  

RUN wget https://www.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz -P /tmp \
    && tar xf /tmp/apache-maven-3.6.3-bin.tar.gz -C /opt \
    && ln -s /opt/apache-maven-3.6.3 /opt/maven


WORKDIR /workspace
COPY / /workspace/

RUN export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-11.0.13.0.8-3.el8_5.x86_64 \
    && export M2_HOME=/opt/maven \
    && export MAVEN_HOME=/opt/maven \
    && export PATH=$M2_HOME/bin:$PATH \
    && make build-project 

WORKDIR /release/
RUN tar -xf /workspace/distro/docker/target/docker/app-files/apicurio-registry-storage-sql-$VERSION-all.tar.gz -C /release


FROM registry.access.redhat.com/ubi8/openjdk-11:latest

ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV AB_ENABLED=jmx_exporter
ENV JAVA_APP_DIR=/deployments

EXPOSE 8080 8778 9779

USER 185


WORKDIR /deployments
COPY --from=builder /release/ /deployments/
