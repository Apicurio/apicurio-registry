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

package io.apicurio.registry.observability;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Centralized OpenTelemetry attribute key definitions for Apicurio Registry.
 * Custom attributes use snake_case with the {@code apicurio.registry.} namespace prefix.
 * Standard OTel semantic convention attributes (e.g., {@code http.response.status_code})
 * use their canonical names without prefix.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/general/attribute-naming/">
 *      OpenTelemetry Attribute Naming</a>
 */
public final class OTelAttributes {

    private OTelAttributes() {
        // utility class
    }

    // -- REST / TracingFilter attributes --

    /** Group ID from the {@code X-Registry-GroupId} header. */
    public static final AttributeKey<String> ATTR_GROUP_ID = AttributeKey.stringKey("apicurio.registry.group_id");

    /** Artifact ID from the {@code X-Registry-ArtifactId} header. */
    public static final AttributeKey<String> ATTR_ARTIFACT_ID = AttributeKey.stringKey("apicurio.registry.artifact_id");

    /** Version from the {@code X-Registry-Version} header. */
    public static final AttributeKey<String> ATTR_VERSION = AttributeKey.stringKey("apicurio.registry.version");

    /** Artifact type from the {@code X-Registry-ArtifactType} header. */
    public static final AttributeKey<String> ATTR_ARTIFACT_TYPE = AttributeKey.stringKey("apicurio.registry.artifact_type");

    /** Request path. */
    public static final AttributeKey<String> ATTR_REQUEST_PATH = AttributeKey.stringKey("apicurio.registry.request.path");

    /** Group ID extracted from path parameters. */
    public static final AttributeKey<String> ATTR_PATH_GROUP_ID = AttributeKey.stringKey("apicurio.registry.path.group_id");

    /** Artifact ID extracted from path parameters. */
    public static final AttributeKey<String> ATTR_PATH_ARTIFACT_ID = AttributeKey.stringKey("apicurio.registry.path.artifact_id");

    /** Version extracted from path parameters. */
    public static final AttributeKey<String> ATTR_PATH_VERSION = AttributeKey.stringKey("apicurio.registry.path.version");

    // -- Storage / StorageTracingInterceptor attributes --

    /** Storage method name. */
    public static final AttributeKey<String> ATTR_STORAGE_METHOD = AttributeKey.stringKey("apicurio.registry.storage.method");

    /** Simple class name of the storage implementation. */
    public static final AttributeKey<String> ATTR_STORAGE_CLASS = AttributeKey.stringKey("apicurio.registry.storage.class");

    /** Full method signature including parameter types. */
    public static final AttributeKey<String> ATTR_STORAGE_METHOD_SIGNATURE = AttributeKey.stringKey("apicurio.registry.storage.method_signature");

    // -- HTTP response attributes (OTel semantic conventions) --

    /** HTTP response status code. */
    public static final AttributeKey<Long> ATTR_HTTP_RESPONSE_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
}
