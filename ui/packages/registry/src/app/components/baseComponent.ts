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
import {Services} from "@apicurio/registry-services";


// tslint:disable-next-line:no-empty-interface
export interface PureComponentProps {
}

// tslint:disable-next-line:no-empty-interface
export interface PureComponentState {
}


/**
 * Base class for all Apicurio Registry UI components.
 */
export abstract class PureComponent<P extends PureComponentProps, S extends PureComponentState, SS = {}> extends React.PureComponent<P, S, SS> {

    private static HISTORY: any = null;

    protected constructor(properties: Readonly<P>) {
        super(properties);
        this.state = this.initializeState();
        this.postConstruct();
    }

    protected abstract initializeState(): S;

    protected postConstruct(): void {
        // Can optionally be overridden by subclasses.
    }

    protected setHistory(history: any): void {
        PureComponent.HISTORY = history;
    }

    protected setSingleState(key: string, value: any, callback?: () => void): void {
        const newState: any = {};
        newState[key] = value;
        this.setMultiState(newState, callback);
    }

    protected setMultiState(newState: any, callback?: () => void): void {
        // Services.getLoggerService().debug("[PureComponent] Setting multi-state: %o", newState);
        this.setState({
            ...newState
        }, callback);
    }

    protected navigateTo = (location: string): () => void => {
        // @ts-ignore
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

}
