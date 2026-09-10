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
}
