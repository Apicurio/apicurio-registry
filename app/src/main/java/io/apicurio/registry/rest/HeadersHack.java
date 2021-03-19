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

import java.util.function.Supplier;

import javax.ws.rs.core.Response;

import io.apicurio.registry.types.ArtifactState;

/**
 * Remove once Quarkus issue #9887 is fixed!
 *
 * @author Ales Justin
 */
public class HeadersHack {
    public static void checkIfDeprecated(
            Supplier<ArtifactState> stateSupplier,
            String groupId,
            String artifactId,
            Object version,
            Response.ResponseBuilder builder
    ) {
        if (stateSupplier.get() == ArtifactState.DEPRECATED) {
            builder.header(Headers.DEPRECATED, true);
            builder.header(Headers.GROUP_ID, groupId);
            builder.header(Headers.ARTIFACT_ID, artifactId);
            builder.header(Headers.VERSION, version != null ? String.valueOf(version) : null);
        }
    }
}
