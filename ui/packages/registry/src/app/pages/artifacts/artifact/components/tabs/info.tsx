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
import {Card, CardBody, CardFooter, CardHeader, Flex, FlexItem} from "@patternfly/react-core";
import {ArtifactTypeIcon} from "../../../components/artifactList";
import {ArtifactMetaData} from "@apicurio/registry-models";
import "./info.css";
import {CodeIcon, DownloadIcon, Remove2Icon} from "@patternfly/react-icons";

/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface InfoTabContentProps extends PureComponentProps {
    artifact: ArtifactMetaData;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface InfoTabContentState extends PureComponentState {
}


/**
 * Models the content of the Artifact Info tab.
 */
export class InfoTabContent extends PureComponent<InfoTabContentProps, InfoTabContentState> {

    constructor(props: Readonly<InfoTabContentProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Flex className="artifact-tab-content">
                <FlexItem>
                    <Card className="artifact-tab-content-card">
                        <CardHeader>
                            <Flex>
                                <FlexItem><ArtifactTypeIcon type={this.props.artifact.type} /></FlexItem>
                                <FlexItem><span>{this.props.artifact.name}</span></FlexItem>
                            </Flex>
                        </CardHeader>
                        <CardBody className="body">
                            <div className="description">{this.props.artifact.description}</div>
                            <div className="metaData">
                                <div className="metaDataItem">
                                    <span className="label">Status</span>
                                    <span className="value">{this.props.artifact.state}</span>
                                </div>
                                <div className="metaDataItem">
                                    <span className="label">Created By</span>
                                    <span className="value">{this.props.artifact.createdBy}</span>
                                </div>
                                <div className="metaDataItem">
                                    <span className="label">Created On</span>
                                    <span className="value">{this.props.artifact.createdOn.toLocaleString()}</span>
                                </div>
                                <div className="metaDataItem">
                                    <span className="label">Modified By</span>
                                    <span className="value">{this.props.artifact.modifiedBy}</span>
                                </div>
                                <div className="metaDataItem">
                                    <span className="label">Modified On</span>
                                    <span className="value">{this.props.artifact.modifiedOn.toLocaleString()}</span>
                                </div>
                            </div>
                        </CardBody>
                        <CardFooter />
                    </Card>
                </FlexItem>
                <FlexItem>
                    <Card className="artifact-tab-actions-card">
                        <CardHeader>
                            <span className="card-title">Actions</span>
                        </CardHeader>
                        <CardBody className="body">
                            <div className="description">The following are the actions available for this artifact (note that some actions are only available for certain artifact types).</div>
                            <ul>
                                <li>
                                    <a href="#">
                                        <DownloadIcon />
                                        <span>&nbsp;</span>
                                        <span>Download</span>
                                    </a>
                                </li>
                                <li>
                                    <a href="#">
                                        <Remove2Icon />
                                        <span>&nbsp;</span>
                                        <span>Delete</span>
                                    </a>
                                </li>
                                <li>
                                    <a href="#">
                                        <CodeIcon />
                                        <span>&nbsp;</span>
                                        <span>Generate Client SDK</span>
                                    </a>
                                </li>
                            </ul>
                        </CardBody>
                    </Card>
                </FlexItem>
            </Flex>
        );
    }

    protected initializeState(): InfoTabContentState {
        return {};
    }
}
