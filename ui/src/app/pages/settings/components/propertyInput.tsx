/**
 * @license
 * Copyright 2022 JBoss Inc
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
import "./configProperty.css";
import {Button, InputGroup, TextInput} from '@patternfly/react-core';
import {PureComponent, PureComponentProps, PureComponentState} from "../../../components";
import {CheckIcon, TimesIcon} from "@patternfly/react-icons";

/**
 * Properties
 */
export interface PropertyInputProps extends PureComponentProps {
    name: string;
    value: string;
    type:
        | 'text'
        | 'number'
        ;
    onChange: (newValue: string) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface PropertyInputState extends PureComponentState {
    currentValue: string;
    isValid: boolean;
    isDirty: boolean;
}

/**
 * Models a single editable config property.
 */
export class PropertyInput extends PureComponent<PropertyInputProps, PropertyInputState> {

    constructor(props: Readonly<PropertyInputProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return <InputGroup>
            <TextInput name={ this.props.name }
                       value={ this.state.currentValue }
                       validated={ this.validated() }
                       onChange={ this.handleInputChange }
                       onKeyDown={ this.handleKeyPress }
                       aria-label="configuration property input"/>
            <Button variant="control" aria-label="save changes button" isDisabled={ !this.state.isDirty || !this.state.isValid } onClick={ this.saveValue }>
                <CheckIcon/>
            </Button>
            <Button variant="control" aria-label="cancel changes button" isDisabled={ !this.state.isDirty } onClick={ this.resetValue }>
                <TimesIcon/>
            </Button>
        </InputGroup>
    }

    protected initializeState(): PropertyInputState {
        return {
            currentValue: this.props.value,
            isDirty: false,
            isValid: true
        };
    }

    private validated(): 'success' | 'warning' | 'error' | 'default' {
        return this.state.isValid ? "default" : "error";
    }

    private saveValue = (): void => {
        this.props.onChange(this.state.currentValue);
        this.setMultiState({
            isDirty: false,
            isValid: true
        });
    };

    private resetValue = (): void => {
        this.setMultiState({
            currentValue: this.props.value,
            isDirty: false,
            isValid: true
        });
    };

    private handleInputChange = (value: string): void => {
        this.setMultiState({
            currentValue: value,
            isDirty: value !== this.props.value,
            isValid: this.validate(value)
        });
    }

    private validate(value: string): boolean {
        if (this.props.type === "text") {
            return value.trim().length > 0;
        } else if (this.props.type === "number") {
            if (value.trim().length == 0) {
                return false;
            }
            const num: Number = Number(value);
            return Number.isInteger(num);
        }
        return true;
    }

    private handleKeyPress = (event: any): void => {
        if (event.code === "Escape") {
            this.resetValue();
        }
        if (event.code === "Enter" && this.state.isDirty && this.state.isValid) {
            this.saveValue();
        }
    };

}
