import { FunctionComponent } from "react";
import "./AgentCardCapabilities.css";
import {
    Flex,
    FlexItem,
    Label,
    Title
} from "@patternfly/react-core";
import { CheckCircleIcon, TimesCircleIcon } from "@patternfly/react-icons";

/**
 * Agent Card capabilities structure
 */
export interface AgentCapabilities {
    streaming?: boolean;
    pushNotifications?: boolean;
    stateTransitionHistory?: boolean;
    [key: string]: boolean | undefined;
}

/**
 * Properties
 */
export type AgentCardCapabilitiesProps = {
    capabilities?: AgentCapabilities;
    showTitle?: boolean;
};

/**
 * Component to display Agent Card capabilities as visual indicators.
 */
export const AgentCardCapabilities: FunctionComponent<AgentCardCapabilitiesProps> = (props: AgentCardCapabilitiesProps) => {
    const capabilities = props.capabilities || {};
    const showTitle = props.showTitle !== false;

    const hasCapabilities = (): boolean => {
        return Object.keys(capabilities).length > 0;
    };

    const formatCapabilityName = (name: string): string => {
        // Convert camelCase to Title Case
        return name.replace(/([A-Z])/g, " $1").replace(/^./, str => str.toUpperCase());
    };

    const renderCapability = (name: string, enabled: boolean | undefined): React.ReactElement => {
        const isEnabled = enabled === true;
        return (
            <FlexItem key={name}>
                <Label
                    color={isEnabled ? "green" : "grey"}
                    icon={isEnabled ? <CheckCircleIcon /> : <TimesCircleIcon />}
                    className="capability-label"
                >
                    {formatCapabilityName(name)}
                </Label>
            </FlexItem>
        );
    };

    if (!hasCapabilities()) {
        return (
            <div className="agent-card-capabilities">
                {showTitle && <Title headingLevel="h4" className="section-title">Capabilities</Title>}
                <span className="empty-state-text">No capabilities defined</span>
            </div>
        );
    }

    return (
        <div className="agent-card-capabilities">
            {showTitle && <Title headingLevel="h4" className="section-title">Capabilities</Title>}
            <Flex className="capabilities-list">
                {Object.entries(capabilities).map(([name, enabled]) =>
                    renderCapability(name, enabled)
                )}
            </Flex>
        </div>
    );
};
