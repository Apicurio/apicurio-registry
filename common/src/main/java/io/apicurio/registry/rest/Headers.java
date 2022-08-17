/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.rest;

import io.apicurio.registry.types.ArtifactState;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

/**
 * @author Ales Justin
 */
public interface Headers {
    String GROUP_ID = "X-Registry-GroupId";
    String ARTIFACT_ID = "X-Registry-ArtifactId";
    String VERSION = "X-Registry-Version";
    String TENANT_ID = "X-Registry-Tenant-Id";
    String ARTIFACT_TYPE = "X-Registry-ArtifactType";
    String HASH_ALGO = "X-Registry-Hash-Algorithm";
    String ARTIFACT_HASH = "X-Registry-Content-Hash";
    String DEPRECATED = "X-Registry-Deprecated";
    String NAME = "X-Registry-Name";
    String NAME_ENCODED = "X-Registry-Name-Encoded";
    String DESCRIPTION = "X-Registry-Description";
    String DESCRIPTION_ENCODED = "X-Registry-Description-Encoded";
    String CONTENT_TYPE = "Content-Type";
    String PRESERVE_GLOBAL_ID = "X-Registry-Preserve-GlobalId";
    String PRESERVE_CONTENT_ID = "X-Registry-Preserve-ContentId";

    default void checkIfDeprecated(
        Supplier<ArtifactState> stateSupplier,
        String groupId,
        String artifactId,
        Number version,
        Response.ResponseBuilder builder
    ) {
        if (stateSupplier.get() == ArtifactState.DEPRECATED) {
            builder.header(Headers.DEPRECATED, true);
            builder.header(Headers.GROUP_ID, groupId);
            builder.header(Headers.ARTIFACT_ID, artifactId);
            builder.header(Headers.VERSION, version);
        }
    }
}
