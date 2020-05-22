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
import {
    Badge,
    DataList,
    DataListAction,
    DataListCell,
    DataListItemCells,
    DataListItemRow
} from '@patternfly/react-core';
import {SearchedArtifact} from "@apicurio/registry-models";
import {Link} from "react-router-dom";
import {ArtifactTypeIcon, PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {Services} from "@apicurio/registry-services";

/**
 * Properties
 */
export interface ArtifactListProps extends PureComponentProps {
    artifacts: SearchedArtifact[];
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactListState extends PureComponentState {
}


/**
 * Models the list of artifacts.
 */
export class ArtifactList extends PureComponent<ArtifactListProps, ArtifactListState> {

    constructor(props: Readonly<ArtifactListProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <DataList aria-label="List of artifacts" className="artifact-list">
                {
                    this.props.artifacts.map( (artifact, idx) =>
                            <DataListItemRow className="artifact-list-item" key={artifact.id}>
                                <DataListItemCells
                                    dataListCells={[
                                        <DataListCell key="type icon" className="type-icon-cell">
                                            <ArtifactTypeIcon type={artifact.type}/>
                                        </DataListCell>,
                                        <DataListCell key="main content" className="content-cell">
                                            <div className="artifact-title">
                                                <span className="name">{artifact.name}</span>
                                                <span className="id">{artifact.id}</span>
                                            </div>
                                            <div className="artifact-description">{artifact.description}</div>
                                            <div className="artifact-tags">
                                                {
                                                    this.labels(artifact).map( label =>
                                                        <Badge key={label} isRead={true}>{label}</Badge>
                                                    )
                                                }
                                            </div>
                                        </DataListCell>
                                    ]}
                                />
                                <DataListAction
                                    id="artifact-actions"
                                    aria-labelledby="artifact-actions"
                                    aria-label="Actions"
                                >
                                    <Link className="pf-c-button pf-m-secondary" data-testid={`artifacts-lnk-view-${idx}`} to={this.artifactLink(artifact)}>View artifact</Link>
                                </DataListAction>
                            </DataListItemRow>
                    )
                }
            </DataList>
        );
    }

    protected initializeState(): ArtifactListState {
        return {};
    }

    private labels(artifact: SearchedArtifact): string[] {
        return artifact.labels ? artifact.labels : [];
    }

    private artifactLink(artifact: SearchedArtifact): string {
        const link: string = `/artifacts/${ encodeURIComponent(artifact.id) }`;
        return link;
    }

}
