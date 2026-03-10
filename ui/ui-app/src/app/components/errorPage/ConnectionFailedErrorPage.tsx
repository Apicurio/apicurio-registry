import React, { FunctionComponent } from "react";
import "./ConnectionFailedErrorPage.css";
import {
    Button,
    ClipboardCopy,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    PageSection,

} from "@patternfly/react-core";
import { NetworkIcon } from "@patternfly/react-icons";
import { ErrorPageProps } from "./ErrorPage.tsx";
import { useConfigService } from "@services/useConfigService.ts";


export const ConnectionFailedErrorPage: FunctionComponent<ErrorPageProps> = () => {

    const config = useConfigService();
    const apiUrl: string = config.artifactsUrl();

    const reload = (): void => {
        window.location.reload();
    };

    return (
        <React.Fragment>
            <PageSection hasBodyWrapper={false} className="ps_error" >
                <div className="centerizer">
                    <EmptyState  headingLevel="h4" icon={NetworkIcon}  titleText="Connection failed">
                        <EmptyStateBody>
                            <p>
                                Connection to the Registry server failed (could not reach the server).  Please
                                check your connection and try again, or report this error to an admin.
                            </p>
                            <p style={{ marginTop: "15px" }}>
                                The UI is configured to connect to:
                                &nbsp;
                                <ClipboardCopy
                                    isReadOnly
                                    style={{ border: "1px solid #ccc", padding: "3px" }}
                                    variant="inline-compact">{ apiUrl }</ClipboardCopy>
                            </p>
                        </EmptyStateBody>
                        <EmptyStateFooter>
                            <EmptyStateActions>
                            </EmptyStateActions>
                            <EmptyStateActions>
                                <Button variant="link"
                                    data-testid="error-btn-reload"
                                    onClick={reload}>Reload the page</Button>
                            </EmptyStateActions>
                        </EmptyStateFooter>
                    </EmptyState>
                </div>
            </PageSection>
        </React.Fragment>
    );

};
