# OpenTelemetry Implementation Plan for Apicurio Registry

## Issue Reference
- GitHub Issue: [#6939 - Observability, logs, metrics, and tracing](https://github.com/Apicurio/apicurio-registry/issues/6939)

## Executive Summary

This document outlines the implementation plan for adding comprehensive OpenTelemetry (OTel) support to Apicurio Registry. The goal is to provide unified observability through distributed tracing, metrics, and structured logging, leveraging Quarkus 3.27's native OpenTelemetry extension.

## Current State Analysis

### Existing Observability Infrastructure

| Component | Current Implementation | Status |
|-----------|----------------------|--------|
| **Metrics** | Micrometer with Prometheus export | Active |
| **Health Checks** | MicroProfile Health (Liveness/Readiness) | Active |
| **Logging** | JBoss Logging with JSON support | Active |
| **Tracing** | None | Not implemented |

### Current Dependencies
- `quarkus-micrometer-registry-prometheus` - Prometheus metrics export
- `quarkus-smallrye-health` - Health endpoints
- `quarkus-logging-json` - JSON logging format

### Custom Metrics Implementation
- `StorageMetricsInterceptor` - Captures storage method call timings
- `MetricsConstants` - Defines metric names and tags
- Health checks: `PersistenceSimpleReadinessCheck`, `StorageLivenessCheck`, etc.

---

## Implementation Plan

### Phase 1: Core OpenTelemetry Integration

#### 1.1 Add OpenTelemetry Dependency

**File: `app/pom.xml`**

Add the Quarkus OpenTelemetry extension:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

#### 1.2 Configure OpenTelemetry

**File: `app/src/main/resources/application.properties`**

```properties
# OpenTelemetry Configuration
quarkus.otel.enabled=true
quarkus.otel.service.name=${apicurio.app.id:apicurio-registry}

# OTLP Exporter Configuration
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
quarkus.otel.exporter.otlp.protocol=grpc

# Tracing Configuration
quarkus.otel.traces.enabled=true
quarkus.otel.traces.sampler=parentbased_always_on

# Metrics Configuration (via OpenTelemetry)
quarkus.otel.metrics.enabled=true

# Logging Configuration (experimental)
quarkus.otel.logs.enabled=false

# Resource Attributes
quarkus.otel.resource.attributes=service.version=${apicurio.app.version},deployment.environment=${quarkus.profile:dev}
```

#### 1.3 Production Profile Configuration

**File: `app/src/main/resources/application-prod.properties`**

```properties
# Production OpenTelemetry Configuration
quarkus.otel.traces.sampler=parentbased_traceidratio
quarkus.otel.traces.sampler.arg=0.1

# Disable logging exporter for production
quarkus.otel.exporter.otlp.logging.enabled=false
```

---

### Phase 2: Micrometer to OpenTelemetry Bridge

#### 2.1 Enable Micrometer-OpenTelemetry Bridge

Quarkus 3.27 supports exporting Micrometer metrics via OpenTelemetry. This allows gradual migration while maintaining backwards compatibility.

**File: `app/pom.xml`**

```xml
<!-- Keep existing Micrometer for backwards compatibility -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Add OpenTelemetry -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

**Configuration:**
```properties
# Enable Micrometer metrics export via OpenTelemetry
quarkus.otel.metrics.enabled=true
```

#### 2.2 Preserve Prometheus Endpoint

Maintain the existing `/q/metrics` Prometheus endpoint for backwards compatibility while also exporting via OTLP.

---

### Phase 3: Custom Tracing Instrumentation

#### 3.1 Storage Layer Tracing

**New File: `app/src/main/java/io/apicurio/registry/metrics/StorageTracingInterceptor.java`**

```java
package io.apicurio.registry.metrics;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@StorageMetricsApply
public class StorageTracingInterceptor {

    @Inject
    Tracer tracer;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        String spanName = "storage." + context.getMethod().getName();

        Span span = tracer.spanBuilder(spanName)
            .setAttribute("storage.method", context.getMethod().getName())
            .setAttribute("storage.class", context.getTarget().getClass().getSimpleName())
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            return context.proceed();
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

#### 3.2 REST API Tracing

REST endpoints are automatically traced by Quarkus OpenTelemetry. Add custom attributes for Apicurio-specific context:

**New File: `app/src/main/java/io/apicurio/registry/rest/TracingFilter.java`**

```java
package io.apicurio.registry.rest;

import io.opentelemetry.api.trace.Span;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TracingFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            // Add Apicurio-specific trace attributes
            String groupId = requestContext.getHeaderString("X-Registry-GroupId");
            String artifactId = requestContext.getHeaderString("X-Registry-ArtifactId");

            if (groupId != null) {
                currentSpan.setAttribute("apicurio.groupId", groupId);
            }
            if (artifactId != null) {
                currentSpan.setAttribute("apicurio.artifactId", artifactId);
            }
        }
    }
}
```

#### 3.3 Kafka Tracing (for KafkaSQL Storage)

**File: `app/src/main/resources/application.properties`**

```properties
# Kafka tracing propagation
quarkus.otel.instrument.kafka=true
```

---

### Phase 4: Custom Metrics via OpenTelemetry API

#### 4.1 OpenTelemetry Metrics Provider

**New File: `app/src/main/java/io/apicurio/registry/metrics/OTelMetricsProvider.java`**

```java
package io.apicurio.registry.metrics;

import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OTelMetricsProvider {

    @Inject
    Meter meter;

    private LongCounter artifactCreatedCounter;
    private LongCounter artifactDeletedCounter;
    private LongCounter schemaValidationCounter;

    public void init() {
        artifactCreatedCounter = meter.counterBuilder("apicurio.artifacts.created")
            .setDescription("Total number of artifacts created")
            .setUnit("1")
            .build();

        artifactDeletedCounter = meter.counterBuilder("apicurio.artifacts.deleted")
            .setDescription("Total number of artifacts deleted")
            .setUnit("1")
            .build();

        schemaValidationCounter = meter.counterBuilder("apicurio.schema.validations")
            .setDescription("Total number of schema validations performed")
            .setUnit("1")
            .build();
    }

    public void recordArtifactCreated(String groupId, String artifactType) {
        artifactCreatedCounter.add(1,
            io.opentelemetry.api.common.Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("groupId"), groupId,
                io.opentelemetry.api.common.AttributeKey.stringKey("artifactType"), artifactType
            ));
    }

    // Additional metric recording methods...
}
```

---

### Phase 5: Logging Integration

#### 5.1 Structured Logging with Trace Context

Enable trace context injection into logs:

**File: `app/src/main/resources/application.properties`**

```properties
# Logging with trace context (experimental in Quarkus 3.27)
quarkus.otel.logs.enabled=true
quarkus.log.console.json=true
quarkus.log.console.json.additional-field.trace_id.type=STRING
quarkus.log.console.json.additional-field.span_id.type=STRING
```

---

### Phase 6: Documentation and Examples

#### 6.1 Update Configuration Documentation

Update `docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.adoc` to include:

- OpenTelemetry configuration properties
- OTLP exporter settings
- Sampling configuration
- Integration examples with popular backends (Jaeger, Grafana Tempo, etc.)

#### 6.2 Docker Compose Examples

Create example Docker Compose files demonstrating:
- Apicurio Registry with Jaeger for tracing
- Apicurio Registry with Grafana LGTM stack (Loki, Grafana, Tempo, Mimir)
- Full observability stack with Prometheus + Grafana + Jaeger

---

### Phase 7: Operator Updates

#### 7.1 Kubernetes Operator Configuration

Update the Apicurio Registry Operator to support:
- OpenTelemetry configuration via CRD
- Auto-injection of OTLP endpoint from OpenTelemetry Collector
- Sidecar configuration for OTel Collector

---

## Configuration Reference

### Minimal Configuration (Development)

```properties
quarkus.otel.enabled=true
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
```

### Production Configuration

```properties
quarkus.otel.enabled=true
quarkus.otel.service.name=apicurio-registry
quarkus.otel.exporter.otlp.endpoint=http://otel-collector:4317
quarkus.otel.exporter.otlp.protocol=grpc
quarkus.otel.traces.sampler=parentbased_traceidratio
quarkus.otel.traces.sampler.arg=0.1
quarkus.otel.metrics.enabled=true
quarkus.otel.resource.attributes=service.version=${apicurio.app.version},deployment.environment=production
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `QUARKUS_OTEL_ENABLED` | Enable/disable OpenTelemetry | `true` |
| `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP collector endpoint | `http://localhost:4317` |
| `QUARKUS_OTEL_TRACES_SAMPLER` | Sampling strategy | `parentbased_always_on` |
| `QUARKUS_OTEL_TRACES_SAMPLER_ARG` | Sampler ratio (for traceidratio) | `1.0` |
| `QUARKUS_OTEL_METRICS_ENABLED` | Enable metrics via OTel | `true` |
| `QUARKUS_OTEL_LOGS_ENABLED` | Enable logs via OTel | `false` |

---

## Migration Path

### Backwards Compatibility

The implementation maintains full backwards compatibility:

1. **Prometheus endpoint** (`/q/metrics`) - Remains available
2. **Health endpoints** (`/q/health/*`) - Unchanged
3. **Existing metrics** - Continue to work via Micrometer

### Deprecation Timeline

| Phase | Action |
|-------|--------|
| 3.2.0 | Add OpenTelemetry support (default disabled) |
| 3.3.0 | Enable OpenTelemetry by default |
| 3.4.0 | Deprecate standalone Micrometer configuration |
| 4.0.0 | OpenTelemetry as primary observability stack |

---

## Testing Strategy

### Unit Tests
- Test custom span creation and attributes
- Test metric recording
- Test configuration loading

### Integration Tests
- End-to-end tracing with test OTLP receiver
- Verify trace propagation across REST calls
- Verify trace propagation in KafkaSQL storage

### Performance Tests
- Measure overhead of tracing instrumentation
- Test sampling configuration effectiveness

---

## Deliverables

1. **Dependencies**: Add `quarkus-opentelemetry` extension
2. **Configuration**: OpenTelemetry properties in application.properties
3. **Custom Instrumentation**: Storage and REST tracing interceptors
4. **Metrics Bridge**: Micrometer to OpenTelemetry bridge configuration
5. **Documentation**: Configuration guide and examples
6. **Docker Examples**: Compose files for observability stacks
7. **Operator Updates**: CRD updates for OpenTelemetry configuration

---

## References

- [Quarkus OpenTelemetry Guide](https://quarkus.io/guides/opentelemetry)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/)
- [OTLP Protocol](https://opentelemetry.io/docs/specs/otlp/)
- [Grafana LGTM Stack](https://grafana.com/oss/lgtm-stack/)
