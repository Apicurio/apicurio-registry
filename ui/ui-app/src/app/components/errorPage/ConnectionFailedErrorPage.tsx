import React, { FunctionComponent } from "react";
import "./ConnectionFailedErrorPage.css";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    PageSection,
    
} from "@patternfly/react-core";
import { NetworkIcon } from "@patternfly/react-icons";
import { ErrorPageProps } from "./ErrorPage.tsx";


export const ConnectionFailedErrorPage: FunctionComponent<ErrorPageProps> = () => {

    const reload = (): void => {
        window.location.reload();
    };

    return (
        <React.Fragment>
            <PageSection hasBodyWrapper={false} className="ps_error" >
                <div className="centerizer">
                    <EmptyState  headingLevel="h4" icon={NetworkIcon}  titleText="Connection failed">
                        <EmptyStateBody>
                            Connection to the Registry server failed (could not reach the server).  Please
                            check your connection and try again, or report this error to an admin.
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
