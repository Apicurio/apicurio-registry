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
import { Link } from "react-router-dom";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";

/**
 * Properties
 */
export interface ArtifactNameProps extends PureComponentProps {
    groupId: string|null;
    id: string;
    name: string;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactNameState extends PureComponentState {
}


/**
 * Models the list of artifacts.
 */
export class ArtifactName extends PureComponent<ArtifactNameProps, ArtifactNameState> {

    constructor(props: Readonly<ArtifactNameProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return this.props.name ? (
            <React.Fragment>
                <Link className="name" data-testid={this.testId("artifacts-lnk-view-")} to={this.artifactLink()}>{this.props.name}</Link>
                <Link className="id" data-testid={this.testId("artifacts-lnk-view-id-")} to={this.artifactLink()}>{this.props.id}</Link>
            </React.Fragment>
        ) : (
            <React.Fragment>
                <Link className="name" data-testid={this.testId("artifacts-lnk-view-")} to={this.artifactLink()}>{this.props.id}</Link>
            </React.Fragment>
        );
    }

    protected initializeState(): ArtifactNameState {
        return {};
    }

    private artifactLink(): string {
        const groupId: string = this.props.groupId == null ? "default" : this.props.groupId;
        const link: string = `/artifacts/${ encodeURIComponent(groupId)}/${ encodeURIComponent(this.props.id) }`;
        return this.linkTo(link);
    }

}
