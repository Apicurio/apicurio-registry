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
import {Button, Flex, FlexItem, FlexModifiers, Text, TextContent, TextVariants} from '@patternfly/react-core';
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {VersionMetaData} from "@apicurio/registry-models";
import {VersionSelector} from "./version-selector";
import {TrashIcon} from "@patternfly/react-icons";
import {IfFeature} from "../../../../components/common/ifFeature";


/**
 * Properties
 */
export interface ArtifactVersionPageHeaderProps extends PureComponentProps {
    artifactId: string;
    onDeleteArtifact: () => void;
    onUploadVersion: () => void;
    version: string;
    versions: VersionMetaData[];
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
                        <Text component={TextVariants.h1}>Artifact Details</Text>
                    </TextContent>
                </FlexItem>
                <FlexItem breakpointMods={[{modifier: FlexModifiers["align-right"]}]}>
                    <VersionSelector version={this.props.version} versions={this.props.versions} artifactId={this.props.artifactId} />
                    <IfFeature feature="readOnly" isNot={true}>
                        <Button id="upload-version-button" variant="secondary" data-testid="header-btn-upload-version" onClick={this.props.onUploadVersion}>Upload new version</Button>
                        <Button id="delete-artifact-button" variant="danger" data-testid="header-btn-delete" onClick={this.props.onDeleteArtifact}><TrashIcon /></Button>
                    </IfFeature>
                </FlexItem>
            </Flex>
        );
    }

    protected initializeState(): ArtifactVersionPageHeaderState {
        return {};
    }
}
