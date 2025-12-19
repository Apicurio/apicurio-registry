# Apicurio Registry with OpenTelemetry Observability

This Docker Compose example demonstrates Apicurio Registry with a complete observability stack:

- **Jaeger** - Distributed tracing backend
- **Prometheus** - Metrics collection
- **Grafana** - Visualization and dashboards

## Quick Start

```bash
docker-compose up -d
```

## Access the Services

| Service | URL | Credentials |
|---------|-----|-------------|
| Apicurio Registry API | http://localhost:8080 | - |
| Apicurio Registry UI | http://localhost:8888 | - |
| Jaeger UI | http://localhost:16686 | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin/admin |

## Observability Features

### Distributed Tracing (Jaeger)

All REST API requests and storage operations are automatically traced. You can:

1. Open Jaeger UI at http://localhost:16686
2. Select "apicurio-registry" from the Service dropdown
3. Click "Find Traces" to see request traces
4. Click on a trace to see the full request flow including:
   - REST endpoint handling
   - Storage layer operations
   - Custom span attributes (groupId, artifactId, etc.)

### Metrics (Prometheus)

Metrics are exported via the `/q/metrics` endpoint and scraped by Prometheus:

1. Open Prometheus at http://localhost:9090
2. Query metrics like:
   - `storage_method_call_seconds` - Storage operation timing
   - `http_server_requests_seconds` - HTTP request timing
   - `apicurio_artifacts_created_total` - Artifact creation count

### Visualization (Grafana)

Grafana is pre-configured with Prometheus and Jaeger datasources:

1. Open Grafana at http://localhost:3000 (admin/admin)
2. Create dashboards using the pre-configured datasources
3. Correlate metrics with traces for deep debugging

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `QUARKUS_OTEL_ENABLED` | Enable/disable OpenTelemetry | `true` |
| `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP collector endpoint | `http://jaeger:4317` |
| `QUARKUS_OTEL_TRACES_SAMPLER` | Sampling strategy | `parentbased_always_on` |
| `QUARKUS_OTEL_TRACES_SAMPLER_ARG` | Sampler ratio (for traceidratio) | `1.0` |
| `QUARKUS_OTEL_METRICS_ENABLED` | Enable metrics via OTel | `true` |
| `QUARKUS_LOG_CONSOLE_JSON` | Enable JSON logging with trace context | `true` |

### Production Recommendations

For production deployments:

1. Use sampling to reduce trace volume:
   ```yaml
   QUARKUS_OTEL_TRACES_SAMPLER: "parentbased_traceidratio"
   QUARKUS_OTEL_TRACES_SAMPLER_ARG: "0.1"  # 10% sampling
   ```

2. Deploy a dedicated OpenTelemetry Collector for better scaling
3. Use persistent storage for Jaeger and Prometheus data
4. Configure Grafana alerting for critical metrics

## Cleanup

```bash
docker-compose down -v
```
