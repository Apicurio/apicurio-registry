import { FunctionComponent } from "react";
import "./CapabilityEditor.css";
import {
    Checkbox,
    Flex,
    FlexItem,
    Title
} from "@patternfly/react-core";
import { AgentCapabilities } from "./AgentCardCapabilities";

/**
 * Properties
 */
export type CapabilityEditorProps = {
    capabilities: AgentCapabilities;
    onChange: (capabilities: AgentCapabilities) => void;
    showTitle?: boolean;
};

/**
 * Component for editing Agent Card capabilities.
 */
export const CapabilityEditor: FunctionComponent<CapabilityEditorProps> = (props: CapabilityEditorProps) => {
    const { capabilities, onChange, showTitle = true } = props;

    const standardCapabilities: Array<{ key: keyof AgentCapabilities; label: string; description: string }> = [
        {
            key: "streaming",
            label: "Streaming",
            description: "Agent supports streaming responses"
        },
        {
            key: "pushNotifications",
            label: "Push Notifications",
            description: "Agent can send push notifications"
        },
        {
            key: "stateTransitionHistory",
            label: "State Transition History",
            description: "Agent maintains state transition history"
        }
    ];

    const handleChange = (key: string, checked: boolean): void => {
        const updated = {
            ...capabilities,
            [key]: checked
        };
        onChange(updated);
    };

    return (
        <div className="capability-editor">
            {showTitle && <Title headingLevel="h4" className="section-title">Capabilities</Title>}
            <Flex direction={{ default: "column" }} className="capabilities-form">
                {standardCapabilities.map(cap => (
                    <FlexItem key={cap.key}>
                        <Checkbox
                            id={`capability-${cap.key}`}
                            label={cap.label}
                            description={cap.description}
                            isChecked={capabilities[cap.key] === true}
                            onChange={(_event, checked) => handleChange(cap.key, checked)}
                        />
                    </FlexItem>
                ))}
            </Flex>
        </div>
    );
};
