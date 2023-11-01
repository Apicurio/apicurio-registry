import { FunctionComponent, ReactNode, useState } from "react";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateHeader,
    EmptyStateIcon,
    EmptyStateVariant
} from "@patternfly/react-core";
import { ExclamationTriangleIcon } from "@patternfly/react-icons";
import { CodeEditor, Language } from "@patternfly/react-code-editor";

export interface TabError {
    errorMessage: string,
    error: any
}

/**
 * Properties
 */
export type ErrorTabContentProps = {
    error?: TabError | undefined
    children?: ReactNode
};

export const ErrorTabContent: FunctionComponent<ErrorTabContentProps> = (props: ErrorTabContentProps) => {
    const [editorHeight] = useState("250px");
    const [editorWidth] = useState("100%");
    const [isShowDetails, setIsShowDetails] = useState(false);

    const errorMessage = (): string => {
        if (props.error) {
            return props.error.errorMessage;
        } else {
            return "Internal server error";
        }
    };

    // Error description can be in node children - if it is missing, default description is used
    const errorDescription = (): ReactNode => {
        if (props.children) {
            return props.children;
        } else {
            return (
                "The content you are trying to visualize is not valid (we could not parse it).  If possible, we'll try to provide you with some more information about " +
                "the problem (see below)."
            );
        }
    };

    const errorDetail = (): string => {
        if (props.error && props.error.error && props.error.error.detail) {
            return props.error.error.detail;
        }else if (props.error && props.error.error && props.error.error.message) {
            return props.error.error.message;
        } else if (props.error && props.error.error) {
            return JSON.stringify(props.error.error, null, 3);
        } else {
            return "Error info not available";
        }
    };

    const showDetails = (): void => {
        setIsShowDetails(true);
    };

    return (
        <div className="centerizer">
            <EmptyState variant={EmptyStateVariant.lg}>
                <EmptyStateHeader titleText={ errorMessage() } headingLevel="h4" icon={<EmptyStateIcon icon={ExclamationTriangleIcon} />} />
                <EmptyStateBody>
                    { errorDescription() }
                </EmptyStateBody>
                <EmptyStateFooter>
                    <EmptyStateActions>
                    </EmptyStateActions>
                    <EmptyStateActions>
                        <Button variant="link"
                            data-testid="error-btn-details"
                            onClick={showDetails}>Show details</Button>
                    </EmptyStateActions>
                </EmptyStateFooter>
            </EmptyState>
            <div className="separator">&nbsp;</div>
            {
                isShowDetails ?
                    <div className="ace-wrapper pf-c-empty-state pf-m-lg" id="ace-wrapper">
                        <CodeEditor
                            isDarkTheme={false}
                            isLineNumbersVisible={true}
                            isReadOnly={true}
                            isMinimapVisible={false}
                            isLanguageLabelVisible={false}
                            code={errorDetail()}
                            language={Language.json}
                            onEditorDidMount={(editor) => { editor.layout(); }}
                            width={editorWidth}
                            height={editorHeight}
                        />
                    </div>
                    :
                    <div/>
            }
        </div>
    );

};



