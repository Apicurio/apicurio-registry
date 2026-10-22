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
import { ErrorPageProps } from "./ErrorPage.tsx";


//export class AccessErrorPage extends ErrorPage {
export const AccessErrorPage: FunctionComponent<ErrorPageProps> = () => {

    const navigateBack = (): void => {
        window.history.back();
    };

    return (
        <React.Fragment>
            <PageSection hasBodyWrapper={false} className="ps_error" >
                <div className="centerizer">
                    <EmptyState  headingLevel="h4" icon={LockedIcon}  titleText="Access permissions needed">
                        <EmptyStateBody>
                            To access this Registry instance, contact your organization administrator.
                        </EmptyStateBody>
                        <EmptyStateFooter>
                            <EmptyStateActions>
                            </EmptyStateActions>
                            <EmptyStateActions>
                                <Button variant="link"
                                    data-testid="error-btn-back"
                                    onClick={navigateBack}>Return to previous page</Button>
                            </EmptyStateActions>
                        </EmptyStateFooter>
                    </EmptyState>
                </div>
            </PageSection>
        </React.Fragment>
    );

};
