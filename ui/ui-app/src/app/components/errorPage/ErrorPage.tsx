import React, { FunctionComponent, useState } from "react";
import "./ErrorPage.css";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateHeader,
    EmptyStateIcon,
    PageSection,
    PageSectionVariants
} from "@patternfly/react-core";
import { ExclamationTriangleIcon } from "@patternfly/react-icons";
import { PageError } from "@app/pages";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { CodeEditor, Language } from "@patternfly/react-code-editor";


export type ErrorPageProps = {
    error: PageError | undefined;
};

export const ErrorPage: FunctionComponent<ErrorPageProps> = (props: ErrorPageProps) => {
    const [isShowDetails, setIsShowDetails] = useState<boolean>(false);

    const appNavigation: AppNavigation = useAppNavigation();

    const errorMessage = (): string => {
        if (props.error) {
            return props.error.errorMessage;
        } else {
            return "Internal server error";
        }
    };

    const errorDetail = (): string => {
        if (props.error && props.error.error && props.error.error.detail) {
            return props.error.error.detail;
        } else if (props.error && props.error.error) {
            return JSON.stringify(props.error.error, null, 3);
        } else {
            return "Error info not available";
        }
    };

    const canShowDetails = (): boolean => {
        return !!(props.error && props.error.error);
    };

    const showDetails = (): void => {
        setIsShowDetails(true);
    };
    
    const reloadPage = (): void => {
        window.location.reload();
    };

    return (
        <React.Fragment>
            <PageSection className="ps_error" variant={PageSectionVariants.light}>
                <div className="centerizer">
                    <EmptyState>
                        <EmptyStateHeader titleText={errorMessage()} headingLevel="h4" icon={<EmptyStateIcon icon={ExclamationTriangleIcon} />} />
                        <EmptyStateBody>
                            Try reloading the page. If the issue persists, reach out to your administrator.
                        </EmptyStateBody>
                        <EmptyStateFooter>
                            <EmptyStateActions>
                                <Button variant="primary" onClick={reloadPage}>Reload page</Button>
                            </EmptyStateActions>
                            <EmptyStateActions>
                                <Button variant="link"
                                    data-testid="error-btn-artifacts"
                                    onClick={() => appNavigation.navigateTo("/")}>Back to artifacts</Button>
                                {
                                    canShowDetails() ?
                                        <Button variant="link"
                                            data-testid="error-btn-details"
                                            onClick={showDetails}>Show details</Button>
                                        :
                                        <span/>
                                }
                            </EmptyStateActions>
                        </EmptyStateFooter>
                    </EmptyState>
                    <div className="separator">&nbsp;</div>
                    {
                        isShowDetails ?
                            <CodeEditor
                                code={errorDetail()}
                                width="700px"
                                isReadOnly={true}
                                isLineNumbersVisible={false}
                                isMinimapVisible={false}
                                onChange={() => {}}
                                language={Language.json}
                                onEditorDidMount={(editor, monaco) => {
                                    editor.layout();
                                    monaco.editor.getModels()[0].updateOptions({ tabSize: 4 });
                                }}
                                height="sizeToFit"
                            />
                            :
                            <div/>
                    }
                </div>
            </PageSection>
        </React.Fragment>
    );

};
