FROM openjdk:8-jdk-alpine

# working directory for gatling
WORKDIR /opt

# gatling version
ENV GATLING_VERSION 3.5.1

# create directory for gatling install
RUN mkdir -p gatling

# install gatling
RUN apk add --update wget bash libc6-compat && \
  mkdir -p /tmp/downloads && \
  wget -q -O /tmp/downloads/gatling-$GATLING_VERSION.zip \
  https://repo1.maven.org/maven2/io/gatling/highcharts/gatling-charts-highcharts-bundle/$GATLING_VERSION/gatling-charts-highcharts-bundle-$GATLING_VERSION-bundle.zip && \
  mkdir -p /tmp/archive && cd /tmp/archive && \
  unzip /tmp/downloads/gatling-$GATLING_VERSION.zip && \
  mv /tmp/archive/gatling-charts-highcharts-bundle-$GATLING_VERSION/* /opt/gatling/ && \
  rm -rf /tmp/*

# change context to gatling directory
WORKDIR  /opt/gatling

# add simulations to the docker image
ADD ../scala /opt/gatling-simulations

# set environment variables
ENV PATH /opt/gatling/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV GATLING_HOME /opt/gatling
ENV REGISTRY_URL=
ENV TEST_USERS=10
ENV TEST_RAMP_TIME=30

# run the test
ENTRYPOINT ["/opt/gatling/bin/gatling.sh", "-sf", "/opt/gatling-simulations", "-s", "simulations.BasicSimulation"]
