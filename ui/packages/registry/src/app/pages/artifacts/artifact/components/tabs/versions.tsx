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
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../../components";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface VersionsTabContentProps extends PureComponentProps {
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface VersionsTabContentState extends PureComponentState {
}


/**
 * Models the content of the Artifact Info tab.
 */
export class VersionsTabContent extends PureComponent<VersionsTabContentProps, VersionsTabContentState> {

    constructor(props: Readonly<VersionsTabContentProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <h1>List of the versions?</h1>
        );
    }

    protected initializeState(): VersionsTabContentState {
        return {};
    }
}
