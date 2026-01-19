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
- **Apicurio Registry**: http://localhost:8083
- **Producer API**: http://localhost:8084
- **Consumer API**: http://localhost:8085

### 4. Generate traces

Send a greeting message:

```bash
curl -X POST "http://localhost:8084/greetings?name=World"
```

Consume a greeting message:

```bash
curl "http://localhost:8085/consumer/greetings"
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

## Verifying the Setup Works

This section provides comprehensive verification steps to ensure distributed tracing is working correctly.

### Verification Scripts

The `scripts/` directory contains ready-to-use verification scripts:

| Script | Description |
|--------|-------------|
| `scripts/smoke-test.sh` | Quick health check and basic message test |
| `scripts/verify-tracing.sh` | Comprehensive tracing verification |
| `scripts/test-scenarios.sh` | Test different tracing scenarios |

### Quick Smoke Test

```bash
# Run the quick smoke test
./scripts/smoke-test.sh
```

This script checks service health and sends a test message. Expected output includes `traceId` fields (32-character hex strings).

### Comprehensive Verification

```bash
# Run full verification with Jaeger checks
./scripts/verify-tracing.sh

# Verbose mode for debugging
./scripts/verify-tracing.sh --verbose

# Skip Jaeger verification (if Jaeger not accessible)
./scripts/verify-tracing.sh --skip-jaeger
```

The verification script checks:
- Service health (Registry, Producer, Consumer)
- Producer trace generation
- Consumer background consumer status
- Message consumption and trace correlation
- Jaeger trace indexing
- Error tracing

### Testing Different Scenarios

Run individual or all test scenarios:

```bash
# Run all scenarios
./scripts/test-scenarios.sh all


# Or run specific scenarios:
./scripts/test-scenarios.sh basic      # Basic message flow
./scripts/test-scenarios.sh batch      # Batch operations
./scripts/test-scenarios.sh detailed   # Custom spans and events
./scripts/test-scenarios.sh error      # Error tracing
```

### Manual Testing

You can also test manually using curl:

```bash
# Basic message flow
curl -X POST "http://localhost:8084/greetings?name=Alice"
curl "http://localhost:8085/consumer/greetings"

# Batch operations
curl -X POST "http://localhost:8084/greetings/batch?baseName=User&count=10"
curl "http://localhost:8085/consumer/greetings/batch?count=5"

# Detailed tracing with custom spans
curl -X POST "http://localhost:8084/greetings/detailed?name=Bob&priority=high"
curl -X POST "http://localhost:8085/consumer/greetings/process"

# Error tracing
curl -X POST "http://localhost:8084/greetings/invalid?errorType=validation"
```

### Observability Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /greetings/health` | Producer health check |
| `GET /greetings/trace-info` | Current trace context info |
| `GET /consumer/health` | Consumer health with queue stats |
| `GET /consumer/stats` | Detailed consumer statistics |
| `GET /consumer/trace-info` | Current trace context info |

### What to Look for in Jaeger

1. **Service Discovery**: All three services should appear in the Service dropdown:
   - `greeting-producer`
   - `greeting-consumer`
   - `apicurio-registry`

2. **Trace Structure**: A complete trace should show:
   - HTTP request span (producer REST endpoint)
   - Custom `create-greeting` span
   - Kafka producer span
   - Kafka consumer span (in background consumer)
   - `process-greeting-message` span with Kafka metadata

3. **Span Attributes**: Look for:
   - `kafka.topic`, `kafka.partition`, `kafka.offset`
   - `greeting.recipient`, `greeting.source`
   - `error.type` (on error spans)

4. **Events**: Spans should contain events like:
   - `greeting-created`
   - `greeting-sent-to-kafka`
   - `message-stored`
   - `validation-completed`

5. **Error Traces**: Error spans show:
   - Red color indicating error status
   - Exception details in span logs
   - `error.handled` event attributes

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

Both applications demonstrate multiple approaches to creating custom spans:

#### Using @WithSpan Annotation (Declarative)

```java
@WithSpan("create-greeting")
private Greeting createGreeting(
        @SpanAttribute("greeting.recipient") String name,
        @SpanAttribute("greeting.trace_id") String traceId) {

    Span.current().setAttribute("greeting.source", "greeting-producer");
    Span.current().addEvent("greeting-object-created");
    // Business logic here
    return greeting;
}
```

#### Using Tracer API (Programmatic)

```java
Span processSpan = tracer.spanBuilder("process-detailed-greeting")
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute("greeting.recipient", name)
        .setAttribute("greeting.priority", priority)
        .startSpan();

try (Scope scope = processSpan.makeCurrent()) {
    processSpan.addEvent("validation-started");
    // Business logic here
    processSpan.addEvent("validation-completed");
    processSpan.setStatus(StatusCode.OK);
} finally {
    processSpan.end();
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

The producer includes a dedicated error endpoint for demonstrating error tracing:

```bash
# Test different error types
curl -X POST "http://localhost:8084/greetings/invalid?errorType=validation"
curl -X POST "http://localhost:8084/greetings/invalid?errorType=schema"
curl -X POST "http://localhost:8084/greetings/invalid?errorType=kafka"
```

Error handling with trace context:

```java
// Create a child span for the failed operation
Span errorSpan = tracer.spanBuilder("process-invalid-greeting")
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute("error.type", errorType)
        .startSpan();

try (Scope scope = errorSpan.makeCurrent()) {
    // Record the exception in the span
    errorSpan.recordException(exception);
    errorSpan.setStatus(StatusCode.ERROR, errorMessage);
    errorSpan.addEvent("error-handled", Attributes.builder()
            .put("error.handled", true)
            .put("error.recovery.action", "returned-error-response")
            .build());
} finally {
    errorSpan.end();
}
```

In Jaeger, error traces appear with:
- Red color indicating error status
- Exception stack traces in span logs
- Custom error attributes and events

## Configuration Reference

### OpenTelemetry Properties

Apicurio Registry is built with all OpenTelemetry signals enabled (traces, metrics, logs), but the OTel SDK is disabled by default at runtime. Enable OTel by setting `QUARKUS_OTEL_SDK_DISABLED=false`.

| Property | Description | Default |
|----------|-------------|---------|
| `quarkus.otel.sdk.disabled` | Disable/enable OTel SDK at runtime | `true` |
| `quarkus.otel.service.name` | Service name in traces | Application name |
| `quarkus.otel.exporter.otlp.endpoint` | OTLP collector endpoint | `http://localhost:4317` |
| `quarkus.otel.exporter.otlp.protocol` | OTLP protocol (grpc or http/protobuf) | `grpc` |
| `quarkus.otel.traces.sampler.arg` | Sampler ratio (0.0 to 1.0) | `0.1` |

**Note:** Signal properties (`traces.enabled`, `metrics.enabled`, `logs.enabled`) are BUILD-TIME properties and cannot be changed at runtime. Use `quarkus.otel.sdk.disabled` for runtime control.

**Note:** Kafka instrumentation is automatic when using `quarkus-opentelemetry` with `quarkus-kafka-client`.

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
3. Ensure `QUARKUS_OTEL_SDK_DISABLED=false` is set for Apicurio Registry

### Schema registration failures

1. Check Apicurio Registry is running: `curl http://localhost:8083/health`
2. Verify Registry URL in application configuration
3. Check network connectivity between services

### Kafka connection issues

1. Verify Kafka is running: `docker compose ps kafka`
2. Check bootstrap servers configuration
3. Ensure topic exists (auto-created on first message)

### Missing trace context in consumer

1. Ensure both `quarkus-opentelemetry` and `quarkus-kafka-client` dependencies are present
2. Check Kafka message headers for trace context
3. Verify OTel is enabled in both producer and consumer (check application.properties has `quarkus.otel.traces.enabled=true`)

## Files Structure

```
otel-tracing/
├── docker-compose.yml           # Docker Compose configuration
├── otel-collector-config.yaml   # OTel Collector configuration
├── pom.xml                      # Parent POM
├── README.md                    # This file
├── scripts/                     # Verification and test scripts
│   ├── smoke-test.sh            # Quick health check and basic test
│   ├── verify-tracing.sh        # Comprehensive tracing verification
│   └── test-scenarios.sh        # Test different tracing scenarios
├── producer/                    # Producer application
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── avro/greeting.avsc   # Avro schema
│       ├── java/.../producer/
│       │   ├── GreetingProducer.java    # Kafka producer with OTel wrapping
│       │   └── GreetingResource.java    # REST endpoints with custom spans
│       └── resources/application.properties
├── consumer/                    # Consumer application
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── avro/greeting.avsc   # Avro schema (copy from producer)
│       ├── java/.../consumer/
│       │   ├── GreetingConsumer.java      # Kafka consumer factory
│       │   ├── GreetingMessageStore.java  # Background consumer with tracing
│       │   └── ConsumerResource.java      # REST endpoints with statistics
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

## API Reference

### Producer Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/greetings?name=X` | Send a greeting message |
| POST | `/greetings/batch?baseName=X&count=N` | Send multiple greetings |
| POST | `/greetings/detailed?name=X&priority=Y` | Send with detailed span attributes |
| POST | `/greetings/invalid?errorType=X` | Simulate errors (validation/schema/kafka) |
| GET | `/greetings/health` | Health check |
| GET | `/greetings/trace-info` | Current trace context |

### Consumer Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/consumer/greetings` | Consume a single greeting |
| GET | `/consumer/greetings/batch?count=N` | Consume multiple greetings |
| POST | `/consumer/greetings/process` | Consume and process with business logic spans |
| GET | `/consumer/stats` | Consumer statistics (received, processed, queue size) |
| GET | `/consumer/health` | Health check with consumer status |
| GET | `/consumer/trace-info` | Current trace context |

## Related Documentation

- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Quarkus OpenTelemetry Guide](https://quarkus.io/guides/opentelemetry)
- [Strimzi Documentation](https://strimzi.io/documentation/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
