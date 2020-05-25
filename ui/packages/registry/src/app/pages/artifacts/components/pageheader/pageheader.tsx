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
import {Button, Flex, FlexItem, FlexModifiers, Text, TextContent, TextVariants} from '@patternfly/react-core';
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {Services} from "@apicurio/registry-services";
import {IfFeature} from "../../../../components/common/ifFeature";


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
                <FlexItem breakpointMods={[{modifier: FlexModifiers["align-right"]}]}>
                    <IfFeature feature="readOnly" isNot={true}>
                        <Button data-testid="btn-header-upload-artifact" variant="secondary" onClick={this.props.onUploadArtifact}>Upload artifact</Button>
                    </IfFeature>
                </FlexItem>
            </Flex>
        );
    }

    protected initializeState(): ArtifactsPageHeaderState {
        return {};
    }
}
