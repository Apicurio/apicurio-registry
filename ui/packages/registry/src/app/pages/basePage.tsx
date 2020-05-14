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
import {ErrorPage, PageError, PureComponent, PureComponentProps, PureComponentState} from "../components";
import {Services} from "@apicurio/registry-services";

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
    isLoading?: boolean;
    isError?: boolean;
    error?: PageError;
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
        if (this.isError()) {
            return (
                <ErrorPage error={this.state.error} />
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

    protected handleServerError(error: any, errorMessage: string): void {
        this.handleError(PageErrorType.Server, error, errorMessage);
    }

    protected getPathParam(paramName: string): string {
        // @ts-ignore
        return decodeURIComponent(this.props.match.params[paramName]);
    }

    protected isLoading(): boolean {
        return this.state.isLoading ? true : false;
    }

    private isError(): boolean {
        return this.state.isError ? true : false;
    }

    private handleError(errorType: PageErrorType, error: any, errorMessage: any): void {
        Services.getLoggerService().error("[PageComponent] Handling an error of type: ", errorType);
        Services.getLoggerService().error("[PageComponent] ", errorMessage);
        Services.getLoggerService().error("[PageComponent] ", error);
        this.setMultiState({
            error: {
                error, errorMessage,
                type: errorType
            },
            isError: true
        });
    }
}
