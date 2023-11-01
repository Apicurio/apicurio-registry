import React, { FunctionComponent, useEffect, useState } from "react";
import { Services } from "@services/services";
import { EmptyState, EmptyStateBody, EmptyStateHeader, EmptyStateIcon, Spinner } from "@patternfly/react-core";
import { ErrorCircleOIcon } from "@patternfly/react-icons";

/**
 * Properties
 */
export type OidcAuthProps = {
    children: React.ReactNode;
};

/**
 * Protect the application with OIDC authentication.
 */
export const ApplicationAuth: FunctionComponent<OidcAuthProps> = (props: OidcAuthProps) => {
    const [authenticating, setAuthenticating] = useState(true);
    const [authenticated, setAuthenticated] = useState(false);

    useEffect(() => {
        Services.getAuthService().authenticate().then(() => {
            setAuthenticated(true);
            setAuthenticating(false);
        }).catch(error => {
            Services.getLoggerService().error("[ApplicationAuth] Authentication failed: ", error);
            setAuthenticating(false);
            setAuthenticated(false);
        });
    }, []);

    const authenticatingEmptyState = (
        <EmptyState>
            <EmptyStateHeader titleText="Loading" headingLevel="h4" />
            <EmptyStateBody>
                <Spinner size="xl" aria-label="Loading spinner" />
            </EmptyStateBody>
        </EmptyState>
    );

    const authenticationFailedEmptyState = (
        <EmptyState>
            <EmptyStateHeader titleText="Empty state" headingLevel="h4" icon={<EmptyStateIcon icon={ErrorCircleOIcon} />} />
            <EmptyStateBody>
                Authentication failed.
            </EmptyStateBody>
        </EmptyState>
    );

    if (authenticating) {
        return authenticatingEmptyState;
    }
    if (!authenticated) {
        return authenticationFailedEmptyState;
    }
    if (authenticated) {
        return props.children;
    }
};
