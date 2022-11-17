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
import "./artifactTypeIcon.css";
import { PureComponent, PureComponentProps, PureComponentState } from "../baseComponent";
import { ArtifactTypes } from "../../../models";

/**
 * Properties
 */
export interface ArtifactTypeIconProps extends PureComponentProps {
    type: string;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactTypeIconState extends PureComponentState {
}


/**
 * Models the list of artifacts.
 */
export class ArtifactTypeIcon extends PureComponent<ArtifactTypeIconProps, ArtifactTypeIconState> {

    constructor(props: Readonly<ArtifactTypeIconProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <div className={ArtifactTypes.getClassNames(this.props.type)} title={ArtifactTypes.getTitle(this.props.type)} />
        );
    }

    protected initializeState(): ArtifactTypeIconState {
        return {};
    }

}

export default ArtifactTypeIcon;
