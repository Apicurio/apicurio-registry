import { FunctionComponent } from "react";
import "./AgentCardViewer.css";
import {
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    Flex,
    FlexItem,
    Label,
    LabelGroup,
    Title
} from "@patternfly/react-core";
import { ExternalLinkAltIcon } from "@patternfly/react-icons";
import { AgentCardCapabilities, AgentCapabilities } from "./AgentCardCapabilities";
import { AgentCardSkills, AgentSkill } from "./AgentCardSkills";
import { AgentCardAuthentication, AgentAuthentication } from "./AgentCardAuthentication";

/**
 * Agent provider structure
 */
export interface AgentProvider {
    organization?: string;
    url?: string;
}

/**
 * A single transport interface through which the agent can be reached.
 * Each entry in supportedInterfaces must supply url, protocolBinding, and
 * protocolVersion to pass the backend's AgentCardContentValidator (A2A Protocol v1.0).
 *
 * `id` is a UI-only stable key used for React list reconciliation and is
 * never sent to the backend.
 */
export interface AgentInterface {
    id?: string;
    url: string;
    protocolBinding: string;
    protocolVersion: string;
}

/**
 * Full Agent Card structure aligned with A2A Protocol v1.0.
 * Fields marked with REQUIRED must be non-empty to pass FULL content validation.
 */
export interface AgentCard {
    /** REQUIRED – human-readable name of the agent. */
    name: string;
    /** REQUIRED by A2A v1.0 contract; optional in the TS model to accommodate partial or legacy cards. */
    description?: string;
    /** REQUIRED by A2A v1.0 contract; optional in the TS model to accommodate partial or legacy cards. */
    version?: string;
    /** Optional – version of the A2A protocol implemented by this agent. */
    protocolVersion?: string;
    /** REQUIRED by A2A v1.0 contract (≥1 entry); optional in the TS model to accommodate partial or legacy cards. */
    supportedInterfaces?: AgentInterface[];
    url?: string;
    provider?: AgentProvider;
    capabilities?: AgentCapabilities;
    skills?: AgentSkill[];
    defaultInputModes?: string[];
    defaultOutputModes?: string[];
    /** @deprecated use securitySchemes / securityRequirements instead */
    authentication?: AgentAuthentication;
    supportsExtendedAgentCard?: boolean;
    iconUrl?: string;
    documentationUrl?: string;
    securitySchemes?: Record<string, object>;
    securityRequirements?: object[];
}

/**
 * Properties
 */
export type AgentCardViewerProps = {
    agentCard: AgentCard;
    className?: string;
};

/**
 * Component to display a complete Agent Card in a structured, read-only view.
 */
export const AgentCardViewer: FunctionComponent<AgentCardViewerProps> = (props: AgentCardViewerProps) => {
    const { agentCard, className } = props;

    const renderModes = (modes: string[] | undefined, label: string): React.ReactElement | null => {
        if (!modes || modes.length === 0) {
            return null;
        }
        return (
            <DescriptionListGroup>
                <DescriptionListTerm>{label}</DescriptionListTerm>
                <DescriptionListDescription>
                    <LabelGroup>
                        {modes.map((mode, index) => (
                            <Label key={index} color="teal" isCompact>
                                {mode}
                            </Label>
                        ))}
                    </LabelGroup>
                </DescriptionListDescription>
            </DescriptionListGroup>
        );
    };

    const renderUrl = (): React.ReactElement | null => {
        if (!agentCard.url) {
            return null;
        }
        return (
            <DescriptionListGroup>
                <DescriptionListTerm>URL</DescriptionListTerm>
                <DescriptionListDescription>
                    <a href={agentCard.url} target="_blank" rel="noopener noreferrer" className="agent-url">
                        {agentCard.url}
                        <ExternalLinkAltIcon className="external-link-icon" />
                    </a>
                </DescriptionListDescription>
            </DescriptionListGroup>
        );
    };

    const renderProvider = (): React.ReactElement | null => {
        if (!agentCard.provider) {
            return null;
        }
        return (
            <DescriptionListGroup>
                <DescriptionListTerm>Provider</DescriptionListTerm>
                <DescriptionListDescription>
                    {agentCard.provider.organization}
                    {agentCard.provider.url && (
                        <a href={agentCard.provider.url} target="_blank" rel="noopener noreferrer" className="provider-url">
                            <ExternalLinkAltIcon className="external-link-icon" />
                        </a>
                    )}
                </DescriptionListDescription>
            </DescriptionListGroup>
        );
    };

    const renderSupportedInterfaces = (): React.ReactElement | null => {
        if (!agentCard.supportedInterfaces || agentCard.supportedInterfaces.length === 0) {
            return null;
        }
        return (
            <DescriptionListGroup>
                <DescriptionListTerm>Supported Interfaces</DescriptionListTerm>
                <DescriptionListDescription>
                    {agentCard.supportedInterfaces.map((iface, index) => (
                        <div key={iface.id || iface.url || index} className="agent-interface">
                            <a href={iface.url} target="_blank" rel="noopener noreferrer" className="agent-url">
                                {iface.url}
                                <ExternalLinkAltIcon className="external-link-icon" />
                            </a>
                            <LabelGroup className="interface-labels">
                                <Label color="purple" isCompact>{iface.protocolBinding}</Label>
                                <Label color="blue" isCompact>v{iface.protocolVersion}</Label>
                            </LabelGroup>
                        </div>
                    ))}
                </DescriptionListDescription>
            </DescriptionListGroup>
        );
    };

    return (
        <Card className={`agent-card-viewer ${className || ""}`}>
            <CardHeader>
                <CardTitle>
                    <Flex>
                        <FlexItem>
                            <Title headingLevel="h2" className="agent-name">
                                {agentCard.name}
                            </Title>
                        </FlexItem>
                        {agentCard.version && (
                            <FlexItem align={{ default: "alignRight" }}>
                                <Label color="blue" isCompact>
                                    v{agentCard.version}
                                </Label>
                            </FlexItem>
                        )}
                    </Flex>
                    {agentCard.description && (
                        <p className="agent-description">{agentCard.description}</p>
                    )}
                </CardTitle>
            </CardHeader>
            <CardBody>
                {/* Basic Info Section */}
                <DescriptionList isCompact className="agent-basic-info">
                    {renderUrl()}
                    {renderProvider()}
                    {renderSupportedInterfaces()}
                    {renderModes(agentCard.defaultInputModes, "Input Modes")}
                    {renderModes(agentCard.defaultOutputModes, "Output Modes")}
                    {agentCard.supportsExtendedAgentCard !== undefined && (
                        <DescriptionListGroup>
                            <DescriptionListTerm>Extended Agent Card</DescriptionListTerm>
                            <DescriptionListDescription>
                                <Label color={agentCard.supportsExtendedAgentCard ? "green" : "grey"} isCompact>
                                    {agentCard.supportsExtendedAgentCard ? "Supported" : "Not Supported"}
                                </Label>
                            </DescriptionListDescription>
                        </DescriptionListGroup>
                    )}
                </DescriptionList>

                <Divider className="section-divider" />

                {/* Capabilities Section */}
                <AgentCardCapabilities capabilities={agentCard.capabilities} />

                <Divider className="section-divider" />

                {/* Skills Section */}
                <AgentCardSkills skills={agentCard.skills} />

                <Divider className="section-divider" />

                {/* Authentication Section */}
                <AgentCardAuthentication authentication={agentCard.authentication} />
            </CardBody>
        </Card>
    );
};
