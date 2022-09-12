import React, { ReactNode } from "react";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import AceEditor from "react-ace";
import "ace-builds/src-noconflict/mode-text";
import "ace-builds/src-noconflict/theme-tomorrow";
import {
    Button,
    EmptyState,
    EmptyStateBody,
    EmptyStateIcon,
    EmptyStateSecondaryActions,
    EmptyStateVariant,
    Title
} from "@patternfly/react-core";
import { ExclamationTriangleIcon } from "@patternfly/react-icons";

export interface TabError {
    errorMessage: string,
    error: any
}

/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ErrorTabContentProps extends PureComponentProps {
    error?: TabError | undefined
    children?: ReactNode
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ErrorTabContentState extends PureComponentState {
    isShowDetails: boolean;
    editorWidth: string;
    editorHeight: string;
}


export class ErrorTabContent extends PureComponent<ErrorTabContentProps, ErrorTabContentState> {

    constructor(props: Readonly<ErrorTabContentProps>) {
        super(props);
    }

    public render() {
        return (
            <div className="centerizer">
                    <EmptyState variant={EmptyStateVariant.large}>
                            <EmptyStateIcon icon={ExclamationTriangleIcon} />
                            <Title headingLevel="h5" size="lg">{ this.errorMessage() }</Title>
                            <EmptyStateBody>
                                { this.errorDescription() }
                            </EmptyStateBody>
                            <EmptyStateSecondaryActions>
                                <Button variant="link"
                                        data-testid="error-btn-details"
                                        onClick={this.showDetails}>Show details</Button>
                            </EmptyStateSecondaryActions>
                        </EmptyState>
                        <div className="separator">&nbsp;</div>
                        {
                            this.state.isShowDetails ?
                                <div className="ace-wrapper pf-c-empty-state pf-m-lg" id="ace-wrapper">
                                    <AceEditor
                                        data-testid="ace-details"
                                        mode="json"
                                        theme="tomorrow"
                                        name="errorDetail"
                                        className="errorDetail"
                                        width={this.state.editorWidth}
                                        height={this.state.editorHeight}
                                        fontSize={14}
                                        showPrintMargin={false}
                                        showGutter={false}
                                        highlightActiveLine={false}
                                        value={this.errorDetail()}
                                        readOnly={true}
                                        setOptions={{
                                            enableBasicAutocompletion: false,
                                            enableLiveAutocompletion: false,
                                            enableSnippets: false,
                                            showLineNumbers: true,
                                            tabSize: 2,
                                            useWorker: false
                                        }}
                                    />
                                </div>
                                :
                                <div/>
                        }
            </div>
        )
    }

    protected initializeState(): ErrorTabContentState {
        return {
            editorHeight: "250px",
            editorWidth: "100%",
            isShowDetails: false
        };
    }

    private errorMessage(): string {
        if (this.props.error) {
            return this.props.error.errorMessage;
        } else {
            return "Internal server error";
        }
    }

    // Error description can be in node children - if it is missing, default description is used
    private errorDescription(): ReactNode {
        if (this.props.children) {
            return this.props.children;
        } else {
            return (
                "The content you are trying to visualize is not valid (we could not parse it).  If possible, we'll try to provide you with some more information about " +
                "the problem (see below)."
            );
        }
    }

    private errorDetail(): string {
        if (this.props.error && this.props.error.error && this.props.error.error.detail) {
            return this.props.error.error.detail;
        }else if (this.props.error && this.props.error.error && this.props.error.error.message) {
            return this.props.error.error.message;
        } else if (this.props.error && this.props.error.error) {
            return JSON.stringify(this.props.error.error, null, 3);
        } else {
            return "Error info not available";
        }
    }

    private showDetails = (): void => {
        this.setSingleState("isShowDetails", true);
    };

}



