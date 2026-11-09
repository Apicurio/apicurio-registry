# Metrics Examples

This example module contains resources that shows how to configure metrics collection and visualization for Apicurio Registry. You might need to adapt them for your specific use-case.

# Docker Compose

## Prerequisites

 - Install *Docker* and *Docker Compose*.
 - You can either build *Apicurio Registry* images locally or use the pre-built images.

## Quickstart

Go to `examples/metrics/src/main/docker-compose` and run

```
docker compose -f compose-metrics.yaml up
```

to start the example.

Docker Compose will start the following containers:

 - PostgreSQL database,
 - [Apicurio Registry server](http://localhost:8080) at `http://localhost:8080`,
 - [Apicurio Registry UI](http://localhost:8888) at `http://localhost:8888`,
 - [Prometheus](http://localhost:9090) at `http://localhost:9090`
 - [Grafana](http://localhost:3000) at `http://localhost:3000`, login with `admin` and `password`.

Grafana will be configured with 2 example dashboards:

- Apicurio Registry :: Executive & Health Dashboard
- Apicurio Registry :: Operations & Details Dashboard

You can use the `examples/metrics/src/main/scripts/metrics-test.sh` to create example traffic to see metrics visualisation in Grafana.

# Metrics that Apicurio Registry provides

- HTTP requests timing. This includes latency, count, percentile information.

  Labels:
  - `status_code_group` - HTTP response status code group, e.g. `2xx`, `5xx`, unless configured otherwise.
  - `path` - REST API path for configured endpoints.
  - `method` HTTP method, for common methods.

- Read/Write database operations
- Resource counters - groups, artifacts, versions, unique pieces of content. (See limits)

# Metrics Overview

Grafana dashboards have been based on information from the [Google SRE book](https://sre.google/sre-book/table-of-contents/). There are 2 example dashboards available:



Four Golden Signals:

- Latency

  *NOTE: Latency information is measured by the Apicurio Registry itself, and might not be accurate under high load. For advanced use-cases, we recommend using a service proxy, such as [Envoy](https://www.envoyproxy.io/) to  collect additional metrics.*

- Traffic

- Errors
  - Requests can be filtered by the HTTP response code. 

- Saturation
  - State of the JVM and database connections.
  
  *NOTE: We are not able to collect storage saturation metrics, such as disk space available to PostgreSQL or Kafka. For advanced use-cases, we recommend collecting additional metrics from your storage service.*

# SLOs and alerts

This example also provides a basic alerting setup, assuming the following SLOs:

- 99% success rate (including uptime)
- 95% requests served under 500ms
