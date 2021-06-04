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
import {Dropdown, DropdownItem, DropdownToggle} from "@patternfly/react-core";
import {CaretDownIcon} from "@patternfly/react-icons";
import {PureComponent, PureComponentProps, PureComponentState} from "../baseComponent";


/**
 * Properties
 */
export interface CompatibilityDropdownProps extends PureComponentProps {
    value: string;
    onSelect: (newValue: string) => void;
}

/**
 * State
 */
export interface CompatibilityDropdownState extends PureComponentState {
    isOpen: boolean;
}


/**
 * Models the content of the Artifact Info tab.
 */
export class CompatibilityDropdown extends PureComponent<CompatibilityDropdownProps, CompatibilityDropdownState> {

    constructor(props: Readonly<CompatibilityDropdownProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        const dropdownItems = [
            <DropdownItem key="BACKWARD"
                          data-testid="rules-compatibility-config-backward"
                          id="BACKWARD">Backward</DropdownItem>,
            <DropdownItem key="BACKWARD_TRANSITIVE"
                          data-testid="rules-compatibility-config-backwardTrans"
                          id="BACKWARD_TRANSITIVE">Backward Transitive</DropdownItem>,
            <DropdownItem key="FORWARD"
                          data-testid="rules-compatibility-config-forward"
                          id="FORWARD">Forward</DropdownItem>,
            <DropdownItem key="FORWARD_TRANSITIVE"
                          data-testid="rules-compatibility-config-forwardTrans"
                          id="FORWARD_TRANSITIVE">Forward Transitive</DropdownItem>,
            <DropdownItem key="FULL"
                          data-testid="rules-compatibility-config-full"
                          id="FULL">Full</DropdownItem>,
            <DropdownItem key="FULL_TRANSITIVE"
                          data-testid="rules-compatibility-config-fullTrans"
                          id="FULL_TRANSITIVE">Full Transitive</DropdownItem>,
        ];
        return (
            <Dropdown
                onSelect={this.onSelect}
                toggle={
                    <DropdownToggle id="toggle-id"
                                    data-testid="rules-compatibility-config-toggle"
                                    onToggle={this.onToggle} iconComponent={CaretDownIcon}>
                        {this.displayValue()}
                    </DropdownToggle>
                }
                isOpen={this.state.isOpen}
                dropdownItems={dropdownItems}
            />
        );
    }

    protected initializeState(): CompatibilityDropdownState {
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
            case "BACKWARD":
                return "Backward";
            case "BACKWARD_TRANSITIVE":
                return "Backward Transitive";
            case "FORWARD":
                return "Forward";
            case "FORWARD_TRANSITIVE":
                return "Forward Transitive";
            case "FULL":
                return "Full";
            case "FULL_TRANSITIVE":
                return "Full Transitive";
        }
        return this.props.value;
    }
}
