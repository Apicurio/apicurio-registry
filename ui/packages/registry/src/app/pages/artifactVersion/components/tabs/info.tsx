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
import {Badge, Button, EmptyStateBody, Flex, FlexItem, Split, SplitItem} from "@patternfly/react-core";
import {ArtifactMetaData, Rule} from "@apicurio/registry-models";
import {DownloadIcon, EditIcon} from "@patternfly/react-icons";
import Moment from "react-moment";
import {IfFeature} from "../../../../components/common/ifFeature";
import {If} from "../../../../components/common/if";

/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface InfoTabContentProps extends PureComponentProps {
    artifact: ArtifactMetaData;
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
                            <SplitItem className="title" isFilled={true}>{this.nameOrId()}</SplitItem>
                            <SplitItem className="actions">
                                <IfAuth isDeveloper={true}>
                                    <IfFeature feature="readOnly" isNot={true}>
                                        <Button id="edit-action"
                                                data-testid="artifact-btn-edit"
                                                title="Edit artifact meta-data"
                                                onClick={this.props.onEditMetaData}
                                                variant="plain"><EditIcon /></Button>
                                    </IfFeature>
                                </IfAuth>
                            </SplitItem>
                        </Split>
                    </div>
                    <div className="description">{this.description()}</div>
                    <div className="metaData">
                        <If condition={this.isArtifactInGroup}>
                            <div className="metaDataItem">
                                <span className="label">Group</span>
                                <span className="value">{this.props.artifact.groupId}</span>
                            </div>
                        </If>
                        <div className="metaDataItem">
                            <span className="label">ID</span>
                            <span className="value">{this.props.artifact.id}</span>
                        </div>
                        <div className="metaDataItem">
                            <span className="label">Status</span>
                            <span className="value">{this.props.artifact.state}</span>
                        </div>
                        <div className="metaDataItem">
                            <span className="label">Created</span>
                            <span className="value"><Moment date={this.props.artifact.createdOn} fromNow={true} /></span>
                        </div>
                        <div className="metaDataItem">
                            <span className="label">Modified</span>
                            <span className="value"><Moment date={this.props.artifact.modifiedOn} fromNow={true} /></span>
                        </div>
                    </div>
                    <div className="labels">
                        {
                            this.labels().map( label =>
                                <Badge key={label} isRead={true}>{label}</Badge>
                            )
                        }
                    </div>
                    <div className="actions">
                        <Button id="download-action"
                                data-testid="artifact-btn-download"
                                title="Download artifact content"
                                onClick={this.props.onDownloadArtifact}
                                variant="primary"><DownloadIcon /> Download</Button>
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
        return this.props.artifact.labels ? this.props.artifact.labels : [];
    }

    private description(): string {
        return this.props.artifact.description ?
            this.props.artifact.description :
            `An artifact of type ${this.props.artifact.type} with no description.`;
    }

    private isArtifactInGroup = (): boolean => {
        const groupId: string|null = this.props.artifact.groupId;
        return groupId != null && groupId != "default";
    };
}
