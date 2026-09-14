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

package io.apicurio.registry.a2a;

/**
 * Constants related to A2A discovery and Agent Cards.
 */
public final class A2AConstants {

    private A2AConstants() {
        // utility class
    }

    public static final String LABEL_AGENT_VISIBILITY = "apicurio.agent.visibility";
    public static final String VISIBILITY_PUBLIC = "public";

    public static final String PREFIX_AGENT_CARD_SKILL = "agent_card:skill:";
    public static final String PREFIX_AGENT_CARD_CAPABILITY = "agent_card:capability:";
    public static final String PREFIX_AGENT_CARD_INPUT_MODE = "agent_card:inputmode:";
    public static final String PREFIX_AGENT_CARD_OUTPUT_MODE = "agent_card:outputmode:";
    public static final String PREFIX_AGENT_CARD_PROTOCOL_BINDING = "agent_card:protocolbinding:";

    /**
     * Label namespace prefix for the OpenAPI-to-Agent-Card auto-generation feature (#7135). Passed as
     * the {@code prefix} argument to {@code mergeArtifactLabels}/{@code mergeVersionLabels} so syncing
     * a generated Agent Card only ever touches labels in this namespace.
     */
    public static final String PREFIX_OPENAPI_AGENT_CARD = "apicurio.a2a.openapi-agent-card.";

    /**
     * Set (value {@code "true"}) on an AGENT_CARD artifact that was auto-generated from an OpenAPI
     * artifact's {@code x-agent-card} extension. Distinguishes registry-managed companion Agent
     * Cards from artifacts a user created directly, so the sync logic never overwrites the latter.
     */
    public static final String LABEL_OPENAPI_AGENT_CARD_GENERATED = PREFIX_OPENAPI_AGENT_CARD + "generated";

    /** The group ID of the OpenAPI artifact a generated AGENT_CARD was derived from. */
    public static final String LABEL_OPENAPI_AGENT_CARD_SOURCE_GROUP_ID = PREFIX_OPENAPI_AGENT_CARD
            + "source-group-id";

    /** The artifact ID of the OpenAPI artifact a generated AGENT_CARD was derived from. */
    public static final String LABEL_OPENAPI_AGENT_CARD_SOURCE_ARTIFACT_ID = PREFIX_OPENAPI_AGENT_CARD
            + "source-artifact-id";

    /**
     * The canonical content hash of the AGENT_CARD content this registry most recently generated and
     * wrote as the latest version. Compared against the latest version's actual current content
     * before syncing, to detect whether a human has since edited it directly (in which case sync is
     * skipped rather than overwriting the edit).
     */
    public static final String LABEL_OPENAPI_AGENT_CARD_GENERATED_HASH = PREFIX_OPENAPI_AGENT_CARD
            + "generated-hash";

    /**
     * Set on the OpenAPI source artifact, pointing at the artifact ID of its generated companion
     * AGENT_CARD (always in the same group as the source).
     */
    public static final String LABEL_OPENAPI_AGENT_CARD_ARTIFACT_ID = PREFIX_OPENAPI_AGENT_CARD + "artifact-id";
}
