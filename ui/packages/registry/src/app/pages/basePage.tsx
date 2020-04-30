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
import {PureComponent, PureComponentProps, PureComponentState} from "../components";
import {
    Flex,
    FlexItem,
    FlexModifiers,
    PageSection,
    PageSectionVariants,
    Text,
    TextArea,
    TextContent,
    TextVariants
} from "@patternfly/react-core";
import {Services} from "@apicurio/registry-services";
import {ExclamationIcon} from "@patternfly/react-icons";
import {Link} from "react-router-dom";

export enum PageErrorType {
    React, Server
}

/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface PageProps extends PureComponentProps {
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface PageState extends PureComponentState {
    isLoading: boolean;
    isError: boolean;
    error: any|null;
    errorInfo: any|null;
    errorType: PageErrorType|null;
}


/**
 * The artifacts page.
 */
export abstract class PageComponent<P extends PageProps, S extends PageState> extends PureComponent<P, S> {

    protected constructor(props: Readonly<P>) {
        super(props);
        this.loadPageData();
    }

    public componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
        this.handleError(PageErrorType.React, error, errorInfo);
    }

    public render(): React.ReactElement {
        if (this.state.isError) {
            return (
                <React.Fragment>
                    <PageSection className="ps_error-header" variant={PageSectionVariants.light}>
                        <Flex className="example-border">
                            <FlexItem>
                                <TextContent>
                                    <Text component={TextVariants.h1}><ExclamationIcon />Error Detected</Text>
                                </TextContent>
                            </FlexItem>
                            <FlexItem breakpointMods={[{modifier: FlexModifiers["align-right"]}]}>
                                <Link to="/">Reload artifacts</Link>
                            </FlexItem>
                        </Flex>
                    </PageSection>
                    <PageSection className="ps_error-body" variant={PageSectionVariants.light}>
                        <h2 style={ {marginBottom: "20px"} }>{ this.errorInfo() }</h2>
                        <TextArea value={ this.error() } readOnly={true} />
                    </PageSection>
                </React.Fragment>
            );
        } else {
            return this.renderPage();
        }
    }

    /**
     * Renders the page content.  Subclasses should implement this instead of render() so that
     * errors are handled/displayed properly.
     */
    protected abstract renderPage(): React.ReactElement;

    protected postConstruct(): void {
        // @ts-ignore
        this.setHistory(this.props.history);
        super.postConstruct();
    }

    protected loadPageData(): void {
        // Default implementation assumes the page does not need to load any data.
        this.setSingleState("isLoading", false);
    }

    protected handleServerError(error: any, errorInfo: string): void {
        this.handleError(PageErrorType.Server, error, errorInfo);
    }

    private handleError(errorType: PageErrorType, error: any, errorInfo: any): void {
        Services.getLoggerService().error("[PageComponent] Handling an error of type: ", errorType);
        Services.getLoggerService().error("[PageComponent] ", errorInfo);
        Services.getLoggerService().error("[PageComponent] ", error);
        this.setMultiState({
            error,
            errorInfo,
            errorType,
            isError: true
        });
    }

    private errorInfo(): string {
        if (this.state.errorInfo) {
            return JSON.stringify(this.state.errorInfo, null, 3);
        } else {
            return "Error info not available";
        }
    }

    private error(): string {
        if (this.state.error) {
            return JSON.stringify(this.state.error, null, 3);
        } else {
            return "Error not available";
        }
    }

}