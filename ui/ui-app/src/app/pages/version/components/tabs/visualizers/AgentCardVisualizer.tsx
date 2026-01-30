import { FunctionComponent } from "react";
import "./AgentCardVisualizer.css";
import { AgentCardViewer, AgentCard } from "@app/components/agentCard";

export type AgentCardVisualizerProps = {
    spec: any;
    className?: string;
};

/**
 * Visualizer for A2A Agent Card content in the documentation tab.
 */
export const AgentCardVisualizer: FunctionComponent<AgentCardVisualizerProps> = (props: AgentCardVisualizerProps) => {
    // Parse the spec into an AgentCard structure
    const agentCard: AgentCard = {
        name: props.spec?.name || "Unknown Agent",
        description: props.spec?.description,
        version: props.spec?.version,
        url: props.spec?.url,
        provider: props.spec?.provider,
        capabilities: props.spec?.capabilities,
        skills: props.spec?.skills,
        defaultInputModes: props.spec?.defaultInputModes,
        defaultOutputModes: props.spec?.defaultOutputModes,
        authentication: props.spec?.authentication,
        supportsExtendedAgentCard: props.spec?.supportsExtendedAgentCard
    };

    return (
        <div className={`agent-card-visualizer ${props.className || ""}`}>
            <AgentCardViewer agentCard={agentCard} />
        </div>
    );
};
