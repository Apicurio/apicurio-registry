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
import {Button, Flex, FlexItem, Text, TextContent, TextVariants} from '@patternfly/react-core';
import {IfAuth, PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {IfFeature} from "../../../../components/common/ifFeature";
import {Link} from "react-router-dom";
import "./pageheader.css";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactsPageHeaderProps extends PureComponentProps {
    onUploadArtifact: () => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactsPageHeaderState extends PureComponentState {
}


/**
 * Models the page header for the Artifacts page.
 */
export class ArtifactsPageHeader extends PureComponent<ArtifactsPageHeaderProps, ArtifactsPageHeaderState> {

    constructor(props: Readonly<ArtifactsPageHeaderProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Flex className="example-border">
                <FlexItem>
                    <TextContent>
                        <Text component={TextVariants.h1}>Artifacts</Text>
                    </TextContent>
                </FlexItem>
                <FlexItem align={{default:"alignRight"}}>

                    <IfAuth isAdmin={true}>
                        <IfFeature feature="roleManagement" is={true}>
                            <Link className="btn-header-roles pf-c-button pf-m-secondary"
                                  data-testid="btn-header-roles" to={this.linkTo(`/roles`)}>Manage access</Link>
                        </IfFeature>
                        <Link className="btn-header-global-rules pf-c-button pf-m-secondary"
                              data-testid="btn-header-global-rules" to={this.linkTo(`/rules`)}>Manage global rules</Link>
                    </IfAuth>

                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="btn-header-upload-artifact" data-testid="btn-header-upload-artifact"
                                    variant="primary" onClick={this.props.onUploadArtifact}>Upload artifact</Button>
                        </IfFeature>
                    </IfAuth>
                </FlexItem>
            </Flex>
        );
    }

    protected initializeState(): ArtifactsPageHeaderState {
        return {};
    }
}
