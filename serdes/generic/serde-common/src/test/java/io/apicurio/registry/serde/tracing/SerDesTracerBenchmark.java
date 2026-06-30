/*
 * Copyright 2024 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.serde.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

class SerDesTracerBenchmark {

    private static final int ITERATIONS = 1_000_000;
    private static final byte[] PAYLOAD = new byte[128];

    @Test
    void benchmarkNoOpTracer() {
        GlobalOpenTelemetry.resetForTest();
        SerDesTracer tracer = new SerDesTracer();

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            tracer.traceSerialize("benchmark-topic", span -> PAYLOAD);
        }
        long elapsed = System.nanoTime() - start;

        double opsPerSec = ITERATIONS / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / ITERATIONS;
        System.out.printf("No-op tracer: %,d ops in %.2fs = %.0f ops/sec (%.1f ns/op)%n",
                ITERATIONS, elapsed / 1_000_000_000.0, opsPerSec, nsPerOp);
    }

    @Test
    void benchmarkActiveTracer() {
        GlobalOpenTelemetry.resetForTest();
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .buildAndRegisterGlobal();

        SerDesTracer tracer = new SerDesTracer();

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            tracer.traceSerialize("benchmark-topic", span -> PAYLOAD);
        }
        long elapsed = System.nanoTime() - start;

        double opsPerSec = ITERATIONS / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / ITERATIONS;
        System.out.printf("Active tracer: %,d ops in %.2fs = %.0f ops/sec (%.1f ns/op) [%d spans]%n",
                ITERATIONS, elapsed / 1_000_000_000.0, opsPerSec, nsPerOp,
                exporter.getFinishedSpanItems().size());

        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void benchmarkBaseline() {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] result = PAYLOAD;
        }
        long elapsed = System.nanoTime() - start;

        double opsPerSec = ITERATIONS / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / ITERATIONS;
        System.out.printf("Baseline (no tracer): %,d ops in %.2fs = %.0f ops/sec (%.1f ns/op)%n",
                ITERATIONS, elapsed / 1_000_000_000.0, opsPerSec, nsPerOp);
    }
}
