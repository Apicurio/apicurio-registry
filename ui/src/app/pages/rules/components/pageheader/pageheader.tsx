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
import {Flex, FlexItem, FlexModifiers, Text, TextContent, TextVariants} from '@patternfly/react-core';
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {Link} from "react-router-dom";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface RulesPageHeaderProps extends PureComponentProps {
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface RulesPageHeaderState extends PureComponentState {
}


/**
 * Models the page header for the Artifacts page.
 */
export class RulesPageHeader extends PureComponent<RulesPageHeaderProps, RulesPageHeaderState> {

    constructor(props: Readonly<RulesPageHeaderProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Flex className="example-border">
                <FlexItem>
                    <TextContent>
                        <Text component={TextVariants.h1}>Global Rules</Text>
                    </TextContent>
                </FlexItem>
                <FlexItem breakpointMods={[{modifier: FlexModifiers["align-right"]}]}>
                    <Link to="/artifacts">Back to artifacts</Link>
                </FlexItem>
            </Flex>
        );
    }

    protected initializeState(): RulesPageHeaderState {
        return {};
    }
}
