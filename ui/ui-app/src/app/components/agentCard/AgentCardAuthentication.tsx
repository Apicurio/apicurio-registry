import { FunctionComponent } from "react";
import "./AgentCardAuthentication.css";
import {
    Label,
    LabelGroup,
    Title
} from "@patternfly/react-core";
import { KeyIcon } from "@patternfly/react-icons";

/**
 * Agent authentication structure
 */
export interface AgentAuthentication {
    schemes?: string[];
    credentials?: string;
}

/**
 * Properties
 */
export type AgentCardAuthenticationProps = {
    authentication?: AgentAuthentication;
    showTitle?: boolean;
};

/**
 * Component to display Agent Card authentication schemes.
 */
export const AgentCardAuthentication: FunctionComponent<AgentCardAuthenticationProps> = (props: AgentCardAuthenticationProps) => {
    const authentication = props.authentication;
    const showTitle = props.showTitle !== false;

    const hasAuthentication = (): boolean => {
        return !!(authentication?.schemes && authentication.schemes.length > 0);
    };

    const formatSchemeName = (scheme: string): string => {
        // Common scheme name formatting
        const schemeMap: Record<string, string> = {
            "bearer": "Bearer Token",
            "api-key": "API Key",
            "apiKey": "API Key",
            "basic": "Basic Auth",
            "oauth2": "OAuth 2.0",
            "oauth": "OAuth",
            "none": "None"
        };
        return schemeMap[scheme.toLowerCase()] || scheme;
    };

    if (!hasAuthentication()) {
        return (
            <div className="agent-card-authentication">
                {showTitle && <Title headingLevel="h4" className="section-title">Authentication</Title>}
                <span className="empty-state-text">No authentication required</span>
            </div>
        );
    }

    return (
        <div className="agent-card-authentication">
            {showTitle && <Title headingLevel="h4" className="section-title">Authentication</Title>}
            <LabelGroup className="auth-schemes-list">
                {authentication!.schemes!.map((scheme, index) => (
                    <Label key={index} color="orange" icon={<KeyIcon />}>
                        {formatSchemeName(scheme)}
                    </Label>
                ))}
            </LabelGroup>
        </div>
    );
};
