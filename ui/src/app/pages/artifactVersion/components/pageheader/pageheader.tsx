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
import "./pageheader.css";
import { Button, Flex, FlexItem, Text, TextContent, TextVariants } from "@patternfly/react-core";
import { IfAuth, PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { VersionSelector } from "./version-selector";
import { IfFeature } from "../../../../components/common/ifFeature";
import { SearchedVersion } from "../../../../../models";


/**
 * Properties
 */
export interface ArtifactVersionPageHeaderProps extends PureComponentProps {
    title: string;
    groupId: string;
    artifactId: string;
    onDeleteArtifact: () => void;
    onUploadVersion: () => void;
    version: string;
    versions: SearchedVersion[];
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactVersionPageHeaderState extends PureComponentState {
}


/**
 * Models the page header for the Artifact page.
 */
export class ArtifactVersionPageHeader extends PureComponent<ArtifactVersionPageHeaderProps, ArtifactVersionPageHeaderState> {

    constructor(props: Readonly<ArtifactVersionPageHeaderProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Flex className="example-border">
                <FlexItem>
                    <TextContent>
                        <Text component={TextVariants.h1}>{ this.props.title }</Text>
                    </TextContent>
                </FlexItem>
                <FlexItem align={{ default : 'alignRight' }}>
                    <VersionSelector version={this.props.version} versions={this.props.versions}
                                     groupId={this.props.groupId} artifactId={this.props.artifactId} />
                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button id="delete-artifact-button" variant="secondary" data-testid="header-btn-delete" onClick={this.props.onDeleteArtifact}>Delete</Button>
                            <Button id="upload-version-button" variant="primary" data-testid="header-btn-upload-version" onClick={this.props.onUploadVersion}>Upload new version</Button>
                        </IfFeature>
                    </IfAuth>
                </FlexItem>
            </Flex>
        );
    }

    protected initializeState(): ArtifactVersionPageHeaderState {
        return {};
    }
}
