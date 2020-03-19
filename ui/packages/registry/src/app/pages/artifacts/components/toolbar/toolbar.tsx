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
    Dropdown,
    DropdownItem,
    DropdownToggle,
    Flex,
    FlexItem,
    FlexModifiers,
    InputGroup, Text, TextContent,
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
    artifactsCount: number
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
            <DataToolbar id="artifacts-toolbar-1" className="artifacts-toolbar">
                <DataToolbarContent>
                    <DataToolbarItem className="filter-item">
                        <InputGroup>
                            <Dropdown
                                onSelect={this.onFilterSelect}
                                toggle={
                                    <DropdownToggle onToggle={this.onFilterToggle}>
                                        {this.state.filterSelection ? this.state.filterSelection : 'Everything'}
                                    </DropdownToggle>
                                }
                                isOpen={this.state.filterIsExpanded}
                                dropdownItems={[
                                    <DropdownItem key="everything" component="button">Everything</DropdownItem>,
                                    <DropdownItem key="name" component="button">Name</DropdownItem>,
                                    <DropdownItem key="description" component="button">Description</DropdownItem>
                                ]}
                            />
                            <TextInput name="textInput1" id="textInput1" type="search"
                                       aria-label="search input example"/>
                            <Button variant={ButtonVariant.control}
                                    aria-label="search button for search input">
                                <SearchIcon/>
                            </Button>
                        </InputGroup>
                    </DataToolbarItem>
                    <DataToolbarItem className="sort-icon-item">
                        <Button variant="plain" aria-label="edit"><SortAlphaDownIcon/></Button>
                    </DataToolbarItem>
                    <DataToolbarItem className="artifact-count-item">
                        <TextContent>
                            <Text>{ this.props.artifactsCount } Artifacts Found</Text>
                        </TextContent>
                    </DataToolbarItem>
                </DataToolbarContent>
            </DataToolbar>
        );
    }

    private onFilterToggle = (isExpanded: boolean): void => {
        const newState: any = {
            ...this.state,
            filterIsExpanded: isExpanded
        };
        this.setState(newState);
    };

    private onFilterSelect = (event: React.SyntheticEvent<HTMLDivElement>): void => {
        const value: string|null = event.currentTarget.textContent;
        const newState: any = {
            ...this.state,
            filterIsExpanded: false,
            filterSelection: value
        };
        this.setState(newState);
    }

}
