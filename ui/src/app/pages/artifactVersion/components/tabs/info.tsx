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
    ArtifactTypeIcon, IfAuth,
    PureComponent,
    PureComponentProps,
    PureComponentState,
    RuleList
} from "../../../../components";
import { Badge, Button, DescriptionList, DescriptionListDescription, DescriptionListGroup, DescriptionListTerm, EmptyStateBody, EmptyStatePrimary, EmptyStateSecondaryActions, Flex, FlexItem, Label, Split, SplitItem } from "@patternfly/react-core";
import { DownloadIcon, EditIcon } from "@patternfly/react-icons";
import Moment from "react-moment";
import { IfFeature } from "../../../../components/common/ifFeature";
import { If } from "../../../../components/common/if";
import { ArtifactMetaData, Rule } from "../../../../../models";

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
                <FlexItem className="artifact-basics">
                    <div className="title-and-type">
                        <Split>
                            <SplitItem className="type"><ArtifactTypeIcon type={this.props.artifact.type} /></SplitItem>
                            <SplitItem className="title" isFilled={true}>Version Metadata</SplitItem>
                            <SplitItem className="actions">
                                <IfAuth isDeveloper={true}>
                                    <IfFeature feature="readOnly" isNot={true}>
                                        <Button id="edit-action"
                                            data-testid="artifact-btn-edit"
                                            title="Edit artifact metadata"
                                            onClick={this.props.onEditMetaData}
                                            variant="plain"><EditIcon /></Button>
                                    </IfFeature>
                                </IfAuth>
                            </SplitItem>
                        </Split>
                    </div>
                    <DescriptionList className="metaData">
                        <DescriptionListGroup>
                            <DescriptionListTerm>Name</DescriptionListTerm>
                            <DescriptionListDescription>{this.props.artifact.name}</DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>ID</DescriptionListTerm>
                            <DescriptionListDescription>{this.props.artifact.id}</DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Description</DescriptionListTerm>
                            <DescriptionListDescription className={this.props.artifact.description == '' ? 'empty-state-text' : ''}>{this.description()}</DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Status</DescriptionListTerm>
                            <DescriptionListDescription>{this.props.artifact.state}</DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Created</DescriptionListTerm>
                            <DescriptionListDescription><Moment date={this.props.artifact.createdOn} fromNow={true} /></DescriptionListDescription>
                        </DescriptionListGroup>
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
                                        <span key={'label' + label}><Label color="blue">{label}</Label>{' '}</span>
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
                                    <span key={key}><Label color="purple">{key + '=' + value}</Label>{' '}</span>
                                )}</DescriptionListDescription>
                            }
                        </DescriptionListGroup>

                    </DescriptionList>

                    <div className="actions">
                        {/* TODO: Move to the content tab */}
                        <Button id="download-action"
                            data-testid="artifact-btn-download"
                            title="Download artifact content"
                            onClick={this.props.onDownloadArtifact}
                            variant="secondary"><DownloadIcon /> Download</Button>
                    </div>
                </FlexItem>
                <FlexItem className="artifact-rules">
                    <div className="rules-label">Content Rules</div>
                    <RuleList rules={this.props.rules}
                        onEnableRule={this.props.onEnableRule}
                        onDisableRule={this.props.onDisableRule}
                        onConfigureRule={this.props.onConfigureRule}
                    />
                </FlexItem>
            </Flex>
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
        return this.props.artifact.description || `No description`;
    }

    private isArtifactInGroup = (): boolean => {
        const groupId: string | null = this.props.artifact.groupId;
        return groupId != null && groupId != "default";
    };
}
