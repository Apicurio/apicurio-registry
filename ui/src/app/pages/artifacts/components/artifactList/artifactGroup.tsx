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
import "./artifactList.css";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";

/**
 * Properties
 */
export interface ArtifactGroupProps extends PureComponentProps {
    groupId: string|null;
    onClick: (groupId: string) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactGroupState extends PureComponentState {
}


/**
 * Models the list of artifacts.
 */
export class ArtifactGroup extends PureComponent<ArtifactGroupProps, ArtifactGroupState> {

    constructor(props: Readonly<ArtifactGroupProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <a className={this.style()} onClick={this.fireOnClick}>{this.props.groupId}</a>
        );
    }

    protected initializeState(): ArtifactGroupState {
        return {};
    }

    private style(): string {
        return !this.props.groupId ? "nogroup" : "group";
    }

    private fireOnClick = (): void => {
        this.props.onClick(this.props.groupId as string);
    };

}
