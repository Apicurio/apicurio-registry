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
import { Services } from "../../services";

/* eslint-disable @typescript-eslint/no-empty-interface */
export interface PureComponentProps {
}

/* eslint-disable @typescript-eslint/no-empty-interface */
export interface PureComponentState {
}


/**
 * Base class for all Apicurio Registry UI components.
 */
export abstract class PureComponent<P extends PureComponentProps, S extends PureComponentState, SS = Record<string, unknown>> extends React.PureComponent<P, S, SS> {

    private static HISTORY: any = null;
    private testIdCounter: number = 1;

    public static setHistory(history: any): void {
        PureComponent.HISTORY = history;
    }

    protected constructor(properties: Readonly<P>) {
        super(properties);
        this.state = this.initializeState();
        this.postConstruct();
    }

    protected abstract initializeState(): S;

    protected testId(prefix: string): string {
        return prefix + this.testIdCounter++;
    }

    protected postConstruct(): void {
        // Can optionally be overridden by subclasses.
    }

    protected setSingleState(key: string, value: any, callback?: () => void): void {
        const newState: any = {};
        newState[key] = value;
        this.setMultiState(newState, callback);
    }

    protected setMultiState<K extends keyof S>(newState: Pick<S, K>, callback?: () => void): void {
        // Services.getLoggerService().debug("[PureComponent] Setting multi-state: %o", newState);
        this.setState({
            ...newState
        }, callback);
    }

    protected navigateTo = (location: string): () => void => {
        return () => {
            const history: any = PureComponent.HISTORY;
            if (history) {
                Services.getLoggerService().info("Navigating to:", location);
                history.push(location);
            } else {
                Services.getLoggerService().warn("Navigation impossible, null/undefined history.");
            }
        };
    };

    protected linkTo(url: string): string {
        return Services.getConfigService().uiNavPrefixPath() + url;
    }

}
