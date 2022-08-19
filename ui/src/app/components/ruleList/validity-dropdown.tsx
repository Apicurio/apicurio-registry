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
import { Dropdown, DropdownItem, DropdownToggle } from "@patternfly/react-core";
import { CaretDownIcon } from "@patternfly/react-icons";
import { PureComponent, PureComponentProps, PureComponentState } from "../baseComponent";


/**
 * Properties
 */
export interface ValidityDropdownProps extends PureComponentProps {
    value: string;
    onSelect: (newValue: string) => void;
}

/**
 * State
 */
export interface ValidityDropdownState extends PureComponentState {
    isOpen: boolean;
}


/**
 * Models the content of the Artifact Info tab.
 */
export class ValidityDropdown extends PureComponent<ValidityDropdownProps, ValidityDropdownState> {

    constructor(props: Readonly<ValidityDropdownProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        const dropdownItems = [
            <DropdownItem key="FULL"
                          tooltip="Syntactic and semantic validation"
                          id="FULL"
                          data-testid="rules-validity-config-full">Full</DropdownItem>,
            <DropdownItem key="SYNTAX_ONLY"
                          tooltip="Only syntactic validation"
                          id="SYNTAX_ONLY"
                          data-testid="rules-validity-config-syntaxOnly">Syntax Only</DropdownItem>,
            <DropdownItem key="NONE"
                          tooltip="No validation"
                          id="NONE"
                          data-testid="rules-validity-config-none">None</DropdownItem>,
        ];
        return (
            <Dropdown
                onSelect={this.onSelect}
                toggle={
                    <DropdownToggle id="toggle-id"
                                    data-testid="rules-validity-config-toggle"
                                    onToggle={this.onToggle} toggleIndicator={CaretDownIcon}>
                        {this.displayValue()}
                    </DropdownToggle>
                }
                isOpen={this.state.isOpen}
                dropdownItems={dropdownItems}
            />
        );
    }

    protected initializeState(): ValidityDropdownState {
        return {
            isOpen: false
        };
    }

    private onToggle = (isOpen: boolean): void => {
        this.setSingleState("isOpen", isOpen);
    };

    private onSelect = (event: any): void => {
        const newValue: string = event && event.currentTarget && event.currentTarget.id ? event.currentTarget.id : "";
        this.props.onSelect(newValue);
        this.onToggle(false);
    };

    private displayValue(): string {
        switch (this.props.value) {
            case "FULL":
                return "Full";
            case "SYNTAX_ONLY":
                return "Syntax Only";
            case "NONE":
                return "None";
        }
        return this.props.value;
    }
}
