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
            <DropdownItem key="NONE"
                          tooltip="No compatibility checking should be performed"
                          data-testid="rules-compatibility-config-none"
                          id="NONE">None</DropdownItem>,
            <DropdownItem key="BACKWARD"
                          tooltip="Clients using the new artifact can read data written using the most recently added artifact"
                          data-testid="rules-compatibility-config-backward"
                          id="BACKWARD">Backward</DropdownItem>,
            <DropdownItem key="BACKWARD_TRANSITIVE"
                          tooltip="Clients using the new artifact can read data written using all previously added artifacts"
                          data-testid="rules-compatibility-config-backwardTrans"
                          id="BACKWARD_TRANSITIVE">Backward Transitive</DropdownItem>,
            <DropdownItem key="FORWARD"
                          tooltip="Clients using the most recently added artifact can read data written using the new artifact"
                          data-testid="rules-compatibility-config-forward"
                          id="FORWARD">Forward</DropdownItem>,
            <DropdownItem key="FORWARD_TRANSITIVE"
                          tooltip="Clients using all previously added artifacts can read data written using the new artifact"
                          data-testid="rules-compatibility-config-forwardTrans"
                          id="FORWARD_TRANSITIVE">Forward Transitive</DropdownItem>,
            <DropdownItem key="FULL"
                          tooltip="The new artifact is forward and backward compatible with the most recently added artifact"
                          data-testid="rules-compatibility-config-full"
                          id="FULL">Full</DropdownItem>,
            <DropdownItem key="FULL_TRANSITIVE"
                          tooltip="The new artifact is forward and backward compatible with all previously added artifacts"
                          data-testid="rules-compatibility-config-fullTrans"
                          id="FULL_TRANSITIVE">Full Transitive</DropdownItem>,
        ];
        return (
            <Dropdown
                onSelect={this.onSelect}
                toggle={
                    <DropdownToggle id="toggle-id"
                                    data-testid="rules-compatibility-config-toggle"
                                    onToggle={this.onToggle} toggleIndicator={CaretDownIcon}>
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
            case "NONE":
                return "None";
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
