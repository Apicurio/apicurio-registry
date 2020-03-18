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
import {LoggerService} from "@apicurio/registry-services";

const loggerService:LoggerService = new LoggerService();

/**
 * Models the page header for the Artifacts page.
 */
export class ArtifactsPageHeader extends React.PureComponent {

    public render(): React.ReactElement {
        loggerService.info("[ArtifactsPageHeader] Rendering");
        return (
            <Flex className="example-border">
                <FlexItem>
                    <TextContent>
                        <Text component={TextVariants.h1}>Artifacts</Text>
                    </TextContent>
                </FlexItem>
                <FlexItem breakpointMods={[{modifier: FlexModifiers["align-right"]}]}>
                    <Button variant="primary">Upload Artifact</Button>
                </FlexItem>
            </Flex>
        );
    }

}
