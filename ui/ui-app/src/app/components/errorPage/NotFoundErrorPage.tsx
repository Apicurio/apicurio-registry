import React, { FunctionComponent } from "react";
import "./NotFoundErrorPage.css";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    PageSection,
    
} from "@patternfly/react-core";
import { QuestionCircleIcon } from "@patternfly/react-icons";
import { ErrorPageProps } from "./ErrorPage.tsx";


export const NotFoundErrorPage: FunctionComponent<ErrorPageProps> = () => {

    const navigateBack = (): void => {
        window.history.back();
    };

    return (
        <React.Fragment>
            <PageSection hasBodyWrapper={false} className="ps_error" >
                <div className="centerizer">
                    <EmptyState  headingLevel="h4" icon={QuestionCircleIcon}  titleText="Resource not found">
                        <EmptyStateBody>
                            The resource you were looking for could not be found.  Perhaps it
                            was deleted?
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
