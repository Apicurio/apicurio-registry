/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.noprofile.otel;

import io.apicurio.registry.AbstractResourceTestBase;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(OpenTelemetryHttpStatusCodeTest.OTelCaptureProfile.class)
class OpenTelemetryHttpStatusCodeTest extends AbstractResourceTestBase {

    private static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
    private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");

    @Inject
    InMemorySpanExporter spanExporter;

    @BeforeEach
    void resetSpans() {
        spanExporter.reset();
    }

    @Test
    void testSuccessResponseHasStatusCodeAttribute() {
        String path = "/apis/registry/v3/system/info";
        given()
                .when()
                .get("/registry/v3/system/info")
                .then()
                .statusCode(200);

        SpanData span = awaitSpanWithPath(path);
        assertNotNull(span, "Expected a span with url.path=" + path);
        assertEquals(200L, span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE));
    }

    @Test
    void testNotFoundResponseHasStatusCodeAttribute() {
        String path = "/apis/registry/v3/groups/nonexistent-group/artifacts/nonexistent-artifact";
        given()
                .when()
                .get("/registry/v3/groups/nonexistent-group/artifacts/nonexistent-artifact")
                .then()
                .statusCode(404);

        SpanData span = awaitSpanWithPath(path);
        assertNotNull(span, "Expected a span with url.path=" + path);
        assertEquals(404L, span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE));
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode(),
                "4xx should not set span status to ERROR");
    }

    private SpanData awaitSpanWithPath(String path) {
        return await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> findSpanWithPath(spanExporter.getFinishedSpanItems(), path),
                        span -> span != null);
    }

    private SpanData findSpanWithPath(List<SpanData> spans, String path) {
        return spans.stream()
                .filter(s -> path.equals(s.getAttributes().get(URL_PATH)))
                .findFirst()
                .orElse(null);
    }

    public static class OTelCaptureProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.otel.enabled", "true",
                    "quarkus.otel.sdk.disabled", "false",
                    "quarkus.otel.traces.sampler", "always_on"
            );
        }
    }

    @ApplicationScoped
    static class InMemorySpanExporterProducer {
        @Produces
        @Singleton
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }
    }
}
