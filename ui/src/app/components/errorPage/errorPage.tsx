/**
 * @license
 * Copyright 2020 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from "react";
import "./errorPage.css";
import {
    Button,
    EmptyState,
    EmptyStateBody,
    EmptyStateIcon,
    EmptyStateSecondaryActions,
    EmptyStateVariant,
    PageSection,
    PageSectionVariants,
    Title
} from "@patternfly/react-core";
import { PureComponent, PureComponentProps, PureComponentState } from "../baseComponent";
import { PageErrorType } from "../../pages/basePage";
import { ExclamationTriangleIcon } from "@patternfly/react-icons";
import AceEditor from "react-ace";
import "ace-builds/src-noconflict/mode-text";
import "ace-builds/src-noconflict/theme-tomorrow";
import { Services } from "../../../services";


export interface PageError {
    type: PageErrorType,
    errorMessage: string,
    error: any
}


// tslint:disable-next-line:no-empty-interface
export interface ErrorPageProps extends PureComponentProps {
    error: PageError|undefined;
}

// tslint:disable-next-line:no-empty-interface
export interface ErrorPageState extends PureComponentState {
    isShowDetails: boolean;
    editorWidth: string;
    editorHeight: string;
    canShowDetails: boolean;
}


export class ErrorPage extends PureComponent<ErrorPageProps, ErrorPageState> {

    constructor(props: Readonly<ErrorPageProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_error" variant={PageSectionVariants.light}>
                    <div className="centerizer">
                        <EmptyState variant={EmptyStateVariant.large}>
                            <EmptyStateIcon icon={ExclamationTriangleIcon} />
                            <Title headingLevel="h5" size="lg">{ this.errorMessage() }</Title>
                            <EmptyStateBody>
                                Try reloading the page. If the issue persists, reach out to your administrator.
                            </EmptyStateBody>
                            <Button variant="primary" onClick={this.reloadPage}>Reload page</Button>
                            <EmptyStateSecondaryActions>
                                <Button variant="link"
                                        data-testid="error-btn-artifacts"
                                        onClick={this.navigateTo(this.linkTo("/"))}>Back to artifacts</Button>
                                {
                                    this.state.canShowDetails ?
                                        <Button variant="link"
                                                data-testid="error-btn-details"
                                                onClick={this.showDetails}>Show details</Button>
                                        :
                                        <span/>
                                }
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
                </PageSection>
            </React.Fragment>
        );
    }

    protected initializeState(): ErrorPageState {
        return {
            editorHeight: "250px",
            editorWidth: "100%",
            isShowDetails: false,
            canShowDetails: !Services.getConfigService().featureMultiTenant()
        };
    }

    private errorMessage(): string {
        if (this.props.error) {
            return this.props.error.errorMessage;
        } else {
            return "Internal server error";
        }
    }

    private errorDetail(): string {
        if (this.props.error && this.props.error.error && this.props.error.error.detail) {
            return this.props.error.error.detail;
        } else if (this.props.error && this.props.error.error) {
            return JSON.stringify(this.props.error.error, null, 3);
        } else {
            return "Error info not available";
        }
    }

    private showDetails = (): void => {
        this.setSingleState("isShowDetails", true);
    };

    protected reloadPage = (): void => {
        window.location.reload();
    };
}
