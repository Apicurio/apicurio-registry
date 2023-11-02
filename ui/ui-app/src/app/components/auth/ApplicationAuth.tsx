import React, { FunctionComponent, useEffect, useState } from "react";
import { Services } from "@services/services";
import { EmptyState, EmptyStateBody, EmptyStateHeader, EmptyStateIcon, Spinner } from "@patternfly/react-core";
import { ErrorCircleOIcon } from "@patternfly/react-icons";
import { If } from "@app/components";

enum AuthState {
    AUTHENTICATING, AUTHENTICATED, AUTHENTICATION_FAILED
}

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
    const [authState, setAuthState] = useState(AuthState.AUTHENTICATING);

    useEffect(() => {
        Services.getAuthService().authenticate().then(() => {
            Services.getLoggerService().info("[ApplicationAuth] Authentication successful.");
            setAuthState(AuthState.AUTHENTICATED);
        }).catch(error => {
            Services.getLoggerService().error("[ApplicationAuth] Authentication failed: ", error);
            setAuthState(AuthState.AUTHENTICATION_FAILED);
        });
    }, []);

    return (
        <>
            <If condition={authState === AuthState.AUTHENTICATING}>
                <EmptyState>
                    <EmptyStateHeader titleText="Loading" headingLevel="h4" />
                    <EmptyStateBody>
                        <Spinner size="xl" aria-label="Loading spinner" />
                    </EmptyStateBody>
                </EmptyState>
            </If>
            <If condition={authState === AuthState.AUTHENTICATION_FAILED}>
                <EmptyState>
                    <EmptyStateHeader titleText="Empty state" headingLevel="h4" icon={<EmptyStateIcon icon={ErrorCircleOIcon} />} />
                    <EmptyStateBody>
                        Authentication failed.
                    </EmptyStateBody>
                </EmptyState>
            </If>
            <If condition={authState === AuthState.AUTHENTICATED} children={props.children} />
        </>
    );
};
