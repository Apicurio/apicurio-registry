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
import {
    Badge,
    Button,
    DataList,
    DataListAction,
    DataListCell,
    DataListCheck,
    DataListItemCells,
    DataListItemRow
} from '@patternfly/react-core';
import {Artifact} from "@apicurio/registry-models";
import {AlertIcon} from "@patternfly/react-core/dist/js/components/Alert/AlertIcon";
import "./artifactList.css";

/**
 * Properties
 */
export interface ArtifactListProps {
    artifacts: Artifact[];
}

/**
 * State
 */
export interface ArtifactListState {
    artifacts: Artifact[];
}


/**
 * Models the list of artifacts.
 */
export class ArtifactList extends React.PureComponent<ArtifactListProps, ArtifactListState> {

    constructor(props: Readonly<ArtifactListProps>) {
        super(props);
        this.state = {
            artifacts: props.artifacts
        };
    }

    public render(): React.ReactElement {
        return (
            <DataList aria-label="List of artifacts" className="artifact-list">
                <DataListItemRow className="artifact-list-item">
                    <DataListCheck aria-labelledby="check-action-item2" name="artifact-item-check" />
                    <DataListItemCells
                        dataListCells={[
                            <DataListCell key="type icon" className="type-icon-cell">
                                <AlertIcon variant="info" className="type-icon" />
                            </DataListCell>,
                            <DataListCell width={5} key="main content" className="content-cell">
                                <div className="artifact-title">Artifact Title</div>
                                <div className="artifact-description">Description content. Dolor sit amet, consectetur adipisicing elit, sed do eiusmod. Dolor sit amet, consectetur adipisicing elit, sed do eiusmod. Dolor sit amet, consectetur adipisicing elit, sed do eiusmod.</div>
                                <div className="artifact-tags">
                                    <Badge isRead={true}>tag-1</Badge>
                                    <Badge isRead={true}>tag-2</Badge>
                                    <Badge isRead={true}>tag-3</Badge>
                                </div>
                            </DataListCell>
                        ]}
                    />
                    <DataListAction
                        id="check-action-action2"
                        aria-labelledby="check-action-item2 check-action-action2"
                        aria-label="Actions"
                    >
                        <Button variant="secondary">View Artifact</Button>
                    </DataListAction>
                </DataListItemRow>
            </DataList>
        );
    }

}
