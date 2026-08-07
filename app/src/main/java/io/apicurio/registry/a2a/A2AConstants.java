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
