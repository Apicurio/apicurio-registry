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
import "./info.css";
import {
    ArtifactTypeIcon,
    IfAuth,
    PureComponent,
    PureComponentProps,
    PureComponentState,
    RuleList
} from "../../../../components";
import {
    Button,
    Card,
    CardBody,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    Label,
    Split,
    SplitItem,
    Truncate
} from "@patternfly/react-core";
import { DownloadIcon, PencilAltIcon } from "@patternfly/react-icons";
import Moment from "react-moment";
import { IfFeature } from "../../../../components/common/ifFeature";
import { ArtifactMetaData, Rule } from "../../../../../models";
import { If } from "../../../../components/common/if";

/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface InfoTabContentProps extends PureComponentProps {
    artifact: ArtifactMetaData;
    isLatest: boolean;
    rules: Rule[];
    onEnableRule: (ruleType: string) => void;
    onDisableRule: (ruleType: string) => void;
    onConfigureRule: (ruleType: string, config: string) => void;
    onDownloadArtifact: () => void;
    onEditMetaData: () => void;
    onChangeOwner: () => void;
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
            <div className="artifact-tab-content">
                <div className="artifact-basics">
                    <Card>
                        <CardTitle>
                            <div className="title-and-type">
                                <Split>
                                    <SplitItem className="type"><ArtifactTypeIcon type={this.props.artifact.type} /></SplitItem>
                                    <SplitItem className="title" isFilled={true}>Version metadata</SplitItem>
                                    <SplitItem className="actions">
                                        <IfAuth isDeveloper={true}>
                                            <IfFeature feature="readOnly" isNot={true}>
                                                <Button id="edit-action"
                                                        data-testid="artifact-btn-edit"
                                                        onClick={this.props.onEditMetaData}
                                                        variant="link"><PencilAltIcon />{" "}Edit</Button>
                                            </IfFeature>
                                        </IfAuth>
                                    </SplitItem>
                                </Split>
                            </div>
                        </CardTitle>
                        <Divider />
                        <CardBody>
                            <DescriptionList className="metaData" isCompact={true}>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Name</DescriptionListTerm>
                                    <DescriptionListDescription className={!this.props.artifact.name ? "empty-state-text" : ""}>{this.artifactName()}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>ID</DescriptionListTerm>
                                    <DescriptionListDescription>{this.props.artifact.id}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Description</DescriptionListTerm>
                                    <DescriptionListDescription className={!this.props.artifact.description ? "empty-state-text" : ""}>{this.description()}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Status</DescriptionListTerm>
                                    <DescriptionListDescription>{this.props.artifact.state}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Created</DescriptionListTerm>
                                    <DescriptionListDescription><Moment date={this.props.artifact.createdOn} fromNow={true} /></DescriptionListDescription>
                                </DescriptionListGroup>
                                <If condition={this.props.artifact.createdBy !== undefined && this.props.artifact.createdBy !== ""}>
                                    <DescriptionListGroup>
                                        <DescriptionListTerm>Owner</DescriptionListTerm>
                                        <DescriptionListDescription>
                                            <span>{this.props.artifact.createdBy}</span>
                                            <span>
                                                <IfAuth isAdminOrOwner={true} owner={this.props.artifact.createdBy}>
                                                    <IfFeature feature="readOnly" isNot={true}>
                                                        <Button id="edit-action"
                                                                data-testid="artifact-btn-edit"
                                                                onClick={this.props.onChangeOwner}
                                                                variant="link"><PencilAltIcon /></Button>
                                                    </IfFeature>
                                                </IfAuth>
                                            </span>
                                        </DescriptionListDescription>
                                    </DescriptionListGroup>
                                </If>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Modified</DescriptionListTerm>
                                    <DescriptionListDescription>{<Moment date={this.props.artifact.modifiedOn} fromNow={true} />}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Global ID</DescriptionListTerm>
                                    <DescriptionListDescription>{this.props.artifact.globalId}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Content ID</DescriptionListTerm>
                                    <DescriptionListDescription>{this.props.artifact.contentId}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Labels</DescriptionListTerm>
                                    {this.labels().length ?
                                        <DescriptionListDescription>{
                                            this.labels().map((label) =>
                                                <Label key={`label-${label}`} color="blue" style={{marginBottom: "2px", marginLeft: "5px"}}>
                                                    <Truncate className="label-truncate" content={label} />
                                                </Label>
                                            )
                                        }</DescriptionListDescription> :
                                        <DescriptionListDescription className="empty-state-text">No labels</DescriptionListDescription>
                                    }

                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Properties</DescriptionListTerm>
                                    {!this.props.artifact.properties || !Object.keys(this.props.artifact.properties).length ?
                                        <DescriptionListDescription className="empty-state-text">No properties</DescriptionListDescription> :
                                        <DescriptionListDescription>{Object.entries(this.props.artifact.properties).map(([key, value]) =>
                                            <Label key={`property-${key}`} color="purple" style={{marginBottom: "2px", marginLeft: "5px"}}>
                                                <Truncate className="property-truncate" content={`${key}=${value}`} />
                                            </Label>
                                        )}</DescriptionListDescription>
                                    }
                                </DescriptionListGroup>
                            </DescriptionList>
                            <div className="actions">
                                <Button id="download-action"
                                        data-testid="artifact-btn-download"
                                        title="Download artifact content"
                                        onClick={this.props.onDownloadArtifact}
                                        variant="secondary"><DownloadIcon /> Download</Button>
                            </div>
                        </CardBody>
                    </Card>
                </div>
                <div className="artifact-rules">
                    <Card>
                        <CardTitle>
                            <div className="rules-label">Content rules</div>
                        </CardTitle>
                        <Divider />
                        <CardBody>
                            <RuleList rules={this.props.rules}
                                      onEnableRule={this.props.onEnableRule}
                                      onDisableRule={this.props.onDisableRule}
                                      onConfigureRule={this.props.onConfigureRule}
                            />
                        </CardBody>
                    </Card>
                </div>
            </div>
        );
    }

    protected initializeState(): InfoTabContentState {
        return {};
    }

    private nameOrId(): string {
        return this.props.artifact.name ? this.props.artifact.name : this.props.artifact.id;
    }

    private labels(): string[] {
        return this.props.artifact.labels || [];
    }

    private description(): string {
        return this.props.artifact.description || "No description";
    }

    private artifactName(): string {
        return this.props.artifact.name || "No name";
    }

    private isArtifactInGroup = (): boolean => {
        const groupId: string | null = this.props.artifact.groupId;
        return groupId != null && groupId !== "default";
    };
}
