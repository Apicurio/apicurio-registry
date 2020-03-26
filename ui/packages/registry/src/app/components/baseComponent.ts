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

/**
 * Base class for all Apicurio Registry UI components.
 */
export abstract class PureComponent<Props, State, SS = {}> extends React.PureComponent<Props, State, SS> {

    protected constructor(properties: Readonly<Props>) {
        super(properties);
    }

    protected setSingleState(key: string, value: any): void {
        const newState: any = {};
        newState[key] = value;
        this.setMultiState(newState);
    }

    protected setMultiState(newState: any): void {
        Services.getLoggerService().debug("[PureComponent] Setting multi-state: %o", newState);
        this.setState({
            ...newState
        });
    }

}
