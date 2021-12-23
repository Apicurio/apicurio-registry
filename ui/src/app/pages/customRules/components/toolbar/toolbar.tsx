/**
 * @license
 * Copyright 2021 Red Hat
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
import React from 'react';
import "./toolbar.css";
import {
    Button,
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from '@patternfly/react-core';
import {IfAuth, IfFeature, PureComponent, PureComponentProps, PureComponentState} from "../../../../components";

/**
 * Properties
 */
export interface CustomRulesPageToolbarProps extends PureComponentProps {
    customRulesCount: number;
    onCreateNewCustomRule: () => void;
}

/**
 * State
 */
export interface CustomRulesPageToolbarState extends PureComponentState {
}

/**
 * Models the toolbar for the Artifacts page.
 */
export class CustomRulesPageToolbar extends PureComponent<CustomRulesPageToolbarProps, CustomRulesPageToolbarState> {

    constructor(props: Readonly<CustomRulesPageToolbarProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Toolbar id="custom-rules-toolbar-1" className="custom-rules-toolbar">
                <ToolbarContent>
                    <ToolbarItem className="upload-artifact-item">
                        <IfAuth isDeveloper={true}>
                            <IfFeature feature="readOnly" isNot={true}>
                                <Button className="btn-header-upload-artifact" data-testid="btn-header-upload-artifact"
                                        variant="primary" onClick={this.props.onCreateNewCustomRule}>Create custom rule</Button>
                            </IfFeature>
                        </IfAuth>
                    </ToolbarItem>
                    <ToolbarItem className="artifact-paging-item">
                        <span>Total custom rules: {this.props.customRulesCount}</span>
                    </ToolbarItem>
                </ToolbarContent>
            </Toolbar>
        );
    }

    protected initializeState(): CustomRulesPageToolbarState {
        return {
        }
    }

}
