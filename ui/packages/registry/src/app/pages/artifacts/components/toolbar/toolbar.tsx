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
import React from 'react';
import {
    Button,
    ButtonVariant,
    DataToolbar,
    DataToolbarContent,
    DataToolbarItem,
    Flex,
    FlexItem,
    FlexModifiers,
    InputGroup,
    Select,
    SelectOption,
    SelectVariant,
    TextInput,
    Toolbar,
    ToolbarGroup,
    ToolbarItem
} from '@patternfly/react-core';
import {SearchIcon, SortAlphaDownIcon} from "@patternfly/react-icons";
import "./toolbar.css";

/**
 * Properties
 */
export interface ArtifactsToolbarProps {

}

/**
 * State
 */
export interface ArtifactsToolbarState {
    filterIsExpanded: boolean;
    filterSelection: string;
}

/**
 * Models the toolbar for the Artifacts page.
 */
export class ArtifactsToolbar extends React.PureComponent<ArtifactsToolbarProps, ArtifactsToolbarState> {

    private filterOptions: any[] = [
        {value: "Everything", disabled: false, isPlaceholder: false},
        {value: 'Name', disabled: false},
        {value: 'Description', disabled: false},
        {value: 'Labels', disabled: false}
    ];

    constructor(props: Readonly<ArtifactsToolbarProps>) {
        super(props);
        this.state = {
            filterIsExpanded: false,
            filterSelection: ""
        };
    }

    public render(): React.ReactElement {
        return (
            <Flex className="artifacts-toolbar">
                <FlexItem>
                    <DataToolbar id="artifacts-toolbar-1">
                        <DataToolbarContent>
                            <DataToolbarItem>
                                <InputGroup>
                                    <DataToolbarItem className="tbi-filter-type">
                                        <Select
                                            variant={SelectVariant.single}
                                            aria-label="Select Input"
                                            onToggle={this.onFilterToggle}
                                            onSelect={this.onFilterSelect}
                                            selections={this.state.filterSelection}
                                            isExpanded={this.state.filterIsExpanded}
                                        >
                                            {this.filterOptions.map((option, index) => (
                                                <SelectOption
                                                    isDisabled={option.disabled}
                                                    key={index}
                                                    value={option.value}
                                                />
                                            ))}
                                        </Select>
                                    </DataToolbarItem>
                                    <TextInput name="textInput1" id="textInput1" type="search"
                                               aria-label="search input example"/>
                                    <Button variant={ButtonVariant.control}
                                            aria-label="search button for search input">
                                        <SearchIcon/>
                                    </Button>
                                </InputGroup>
                            </DataToolbarItem>
                            <DataToolbarItem>
                                <Button variant="plain" aria-label="edit"><SortAlphaDownIcon/></Button>
                            </DataToolbarItem>
                        </DataToolbarContent>
                    </DataToolbar>
                </FlexItem>
                <FlexItem breakpointMods={[{modifier: FlexModifiers["align-right"]}]}>
                    <Toolbar>
                        <ToolbarGroup>
                            <ToolbarItem>13 Artifacts Found</ToolbarItem>
                        </ToolbarGroup>
                    </Toolbar>
                </FlexItem>
            </Flex>
        );
    }

    private onFilterToggle = (isExpanded: boolean): void => {
        const newState: any = {
            ...this.state,
            filterIsExpanded: isExpanded
        };
        this.setState(newState);
    };

    private onFilterSelect = (event: any, selection: any): void => {
        const newState: any = {
            ...this.state,
            filterIsExpanded: false,
            filterSelection: selection
        };
        this.setState(newState);
    }

}
