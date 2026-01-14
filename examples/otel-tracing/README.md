# OpenTelemetry Distributed Tracing Example

This example demonstrates end-to-end distributed tracing across a complete stack incorporating Kafka (using Strimzi images) and Apicurio Registry. Traces flow from message producers through consumers and schema validation operations, allowing you to visualize the complete request flow in Jaeger.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Quarkus        │────▶│     Kafka       │────▶│    Quarkus      │
│  Producer       │     │   (Strimzi)     │     │    Consumer     │
└────────┬────────┘     └─────────────────┘     └────────┬────────┘
         │                                               │
         │              ┌─────────────────┐              │
         └─────────────▶│   Apicurio      │◀─────────────┘
                        │   Registry      │
                        │  (OTel enabled) │
                        └────────┬────────┘
                                 │
                        ┌────────▼────────┐
                        │   OpenTelemetry │
                        │   Collector     │
                        └────────┬────────┘
                                 │
                        ┌────────▼────────┐
                        │     Jaeger      │
                        │  (Visualization)│
                        └─────────────────┘
```

## Components

- **Jaeger**: Distributed tracing backend for visualization
- **OpenTelemetry Collector**: Collects, processes, and exports telemetry data
- **Kafka**: Message broker (using Strimzi images)
- **PostgreSQL**: Database for Apicurio Registry
- **Apicurio Registry**: Schema registry with OpenTelemetry enabled
- **Producer Application**: Quarkus application that produces Avro messages
- **Consumer Application**: Quarkus application that consumes Avro messages

## Prerequisites

- Docker and Docker Compose (for local deployment)
- Kubernetes cluster with Strimzi operator (for K8s deployment)
- Java 17 and Maven (for building applications)

## Quick Start with Docker Compose

### 1. Build the applications

First, build the producer and consumer applications:

```bash
cd examples/otel-tracing

# Build producer
cd producer
mvn clean package -DskipTests
cd ..

# Build consumer
cd consumer
mvn clean package -DskipTests
cd ..
```

### 2. Start the infrastructure

Start all services using Docker Compose:

```bash
docker compose up -d
```

Wait for all services to be healthy:

```bash
docker compose ps
```

### 3. Access the services

- **Jaeger UI**: http://localhost:16686
- **Apicurio Registry**: http://localhost:8080
- **Producer API**: http://localhost:8081
- **Consumer Stats**: http://localhost:8082

### 4. Generate traces

Send a greeting message:

```bash
curl -X POST "http://localhost:8081/greetings?name=World"
```

Send multiple messages:

```bash
curl -X POST "http://localhost:8081/greetings/batch?baseName=Test&count=10"
```

### 5. View traces in Jaeger

Open http://localhost:16686 in your browser and:

1. Select service: `greeting-producer`, `greeting-consumer`, or `apicurio-registry`
2. Click "Find Traces"
3. Click on a trace to see the complete flow

### 6. Stop the environment

```bash
docker compose down -v
```

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster (Minikube, Kind, or cloud provider)
- [Strimzi Operator](https://strimzi.io/) installed
- kubectl configured

### 1. Install Strimzi (if not already installed)

```bash
kubectl create namespace strimzi
kubectl apply -f 'https://strimzi.io/install/latest?namespace=strimzi' -n strimzi
```

### 2. Build and push container images

```bash
# Build producer image
cd producer
docker build -t your-registry/otel-tracing-producer:latest .
docker push your-registry/otel-tracing-producer:latest
cd ..

# Build consumer image
cd consumer
docker build -t your-registry/otel-tracing-consumer:latest .
docker push your-registry/otel-tracing-consumer:latest
cd ..
```

### 3. Update image references

Edit `k8s/producer.yaml` and `k8s/consumer.yaml` to use your container registry.

### 4. Deploy with Kustomize

```bash
kubectl apply -k k8s/
```

### 5. Wait for deployments

```bash
kubectl get pods -n otel-tracing-demo -w
```

### 6. Access services

```bash
# Port-forward Jaeger UI
kubectl port-forward -n otel-tracing-demo svc/jaeger 16686:16686

# Port-forward Producer API
kubectl port-forward -n otel-tracing-demo svc/greeting-producer 8081:8080
```

### 7. Generate and view traces

Same as Docker Compose steps 4-5.

### 8. Cleanup

```bash
kubectl delete -k k8s/
```

## Trace Propagation Patterns

### Automatic Context Propagation

The OpenTelemetry instrumentation for Kafka automatically:

1. **Producer side**: Injects trace context (trace ID, span ID) into Kafka message headers
2. **Consumer side**: Extracts trace context from Kafka message headers and creates child spans

This creates a continuous trace across service boundaries.

### Registry Correlation

When the producer or consumer communicates with Apicurio Registry:

1. The HTTP client instrumentation propagates trace context in HTTP headers
2. Registry operations (schema registration, lookup) appear as child spans
3. Schema caching is visible in trace timing (first call slower, subsequent calls faster)

### Custom Spans

Both applications demonstrate creating custom spans for business logic:

```java
Span span = tracer.spanBuilder("create-greeting")
        .setAttribute("greeting.name", name)
        .startSpan();

try (Scope scope = span.makeCurrent()) {
    // Business logic here
    span.addEvent("greeting-created");
} finally {
    span.end();
}
```

## Sampling Strategies

The example uses `parentbased_always_on` sampling, which means:

- All traces are sampled (100% sampling rate)
- Child spans inherit the sampling decision from parent spans

For production environments, consider:

- **ratio**: Sample a percentage of traces (e.g., 10%)
- **parentbased_traceidratio**: Ratio-based with parent inheritance
- **always_off**: Disable tracing entirely

Configure via environment variable:

```yaml
QUARKUS_OTEL_TRACES_SAMPLER: "parentbased_traceidratio"
QUARKUS_OTEL_TRACES_SAMPLER_ARG: "0.1"  # 10% sampling
```

## Error Scenario Tracing

The consumer demonstrates error handling with trace context:

1. Validation failures are recorded as span events
2. Exceptions are captured with `span.recordException()`
3. Error status is propagated through the trace

To test error scenarios, modify the producer to send invalid data and observe the error traces in Jaeger.

## Configuration Reference

### OpenTelemetry Properties

| Property | Description | Default |
|----------|-------------|---------|
| `quarkus.otel.enabled` | Enable/disable OTel | `true` |
| `quarkus.otel.service.name` | Service name in traces | Application name |
| `quarkus.otel.exporter.otlp.endpoint` | OTLP collector endpoint | `http://localhost:4317` |
| `quarkus.otel.traces.sampler` | Sampling strategy | `parentbased_always_on` |

**Note:** Kafka instrumentation is automatic when using `quarkus-opentelemetry` with `quarkus-messaging-kafka`.

### Apicurio Registry Properties

| Property | Description |
|----------|-------------|
| `apicurio.registry.url` | Registry API endpoint |
| `apicurio.registry.auto-register` | Auto-register schemas |
| `apicurio.registry.artifact.resolver-strategy` | Strategy for artifact resolution |

## Troubleshooting

### No traces appearing in Jaeger

1. Check OTel Collector logs: `docker compose logs otel-collector`
2. Verify OTLP endpoint configuration
3. Ensure `quarkus.otel.enabled=true`

### Schema registration failures

1. Check Apicurio Registry is running: `curl http://localhost:8080/health`
2. Verify Registry URL in application configuration
3. Check network connectivity between services

### Kafka connection issues

1. Verify Kafka is running: `docker compose ps kafka`
2. Check bootstrap servers configuration
3. Ensure topic exists (auto-created on first message)

### Missing trace context in consumer

1. Ensure both `quarkus-opentelemetry` and `quarkus-messaging-kafka` dependencies are present
2. Check Kafka message headers for trace context
3. Verify `quarkus.otel.enabled=true` in both producer and consumer

## Files Structure

```
otel-tracing/
├── docker-compose.yml           # Docker Compose configuration
├── otel-collector-config.yaml   # OTel Collector configuration
├── pom.xml                      # Parent POM
├── README.md                    # This file
├── producer/                    # Producer application
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── avro/greeting.avsc   # Avro schema
│       ├── java/.../producer/   # Java source files
│       └── resources/application.properties
├── consumer/                    # Consumer application
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/.../consumer/   # Java source files
│       └── resources/application.properties
└── k8s/                         # Kubernetes manifests
    ├── kustomization.yaml
    ├── namespace.yaml
    ├── jaeger.yaml
    ├── otel-collector.yaml
    ├── kafka.yaml
    ├── registry.yaml
    ├── producer.yaml
    └── consumer.yaml
```

## Related Documentation

- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Quarkus OpenTelemetry Guide](https://quarkus.io/guides/opentelemetry)
- [Strimzi Documentation](https://strimzi.io/documentation/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
