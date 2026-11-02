import React, { FunctionComponent } from "react";
import "./ErrorPage.css";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    PageSection,
} from "@patternfly/react-core";
import { LockedIcon } from "@patternfly/react-icons";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";

export const ReadOnlyErrorPage: FunctionComponent = () => {
    const config: ConfigService = useConfigService();
    const appNavigation: AppNavigation = useAppNavigation();

    const reason = config.featureReadOnlyReason();
    const storageName = config.features().storageName;
    const isGitOps = reason === "storage" && storageName === "gitops";

    const message = isGitOps
        ? "This registry is managed via GitOps. To make changes, update your source Git repository."
        : "This registry is in read-only mode. Write operations are not available.";

    const navigateToDashboard = (): void => {
        appNavigation.navigateTo("/dashboard");
    };

    return (
        <React.Fragment>
            <PageSection hasBodyWrapper={false} className="ps_error">
                <div className="centerizer">
                    <EmptyState headingLevel="h4" icon={LockedIcon} titleText="Read-Only Mode">
                        <EmptyStateBody>
                            {message}
                        </EmptyStateBody>
                        <EmptyStateFooter>
                            <EmptyStateActions>
                                <Button variant="link"
                                    data-testid="error-btn-dashboard"
                                    onClick={navigateToDashboard}>Return to Dashboard</Button>
                            </EmptyStateActions>
                        </EmptyStateFooter>
                    </EmptyState>
                </div>
            </PageSection>
        </React.Fragment>
    );
};
