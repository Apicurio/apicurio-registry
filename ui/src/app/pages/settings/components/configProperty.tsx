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
import {
    Button, Checkbox,
    Modal, NumberInput, TextInput
} from '@patternfly/react-core';
import { TableComposable, Thead, Tbody, Tr, Th, Td } from '@patternfly/react-table';
import {ConfigurationProperty} from "../../../../models/configurationProperty.model";
import {PureComponent, PureComponentProps, PureComponentState} from "../../../components";
import {PropertyInput} from "./propertyInput";

/**
 * Properties
 */
export interface ConfigPropertyProps extends PureComponentProps {
    property: ConfigurationProperty;
    onChange: (property: ConfigurationProperty, newValue: string) => void;
    onReset: (property: ConfigurationProperty) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ConfigPropertyState extends PureComponentState {
    isChecked: boolean | undefined;
}

/**
 * Models a single editable config property.
 */
export class ConfigProperty extends PureComponent<ConfigPropertyProps, ConfigPropertyState> {

    constructor(props: Readonly<ConfigPropertyProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        if (this.props.property.type === "java.lang.Boolean") {
            return this.renderBooleanProp();
        } else if (this.props.property.type === "java.lang.Integer") {
            return this.renderStringProp("number");
        } else if (this.props.property.type === "java.lang.Long") {
            return this.renderStringProp("number");
        } else {
            return this.renderStringProp("text");
        }
    }

    protected initializeState(): ConfigPropertyState {
        return {
            isChecked: this.props.property?.value === "true"
        };
    }

    private renderBooleanProp(): React.ReactElement {
        return  <div className="configuration-property boolean-property">
                    <Checkbox id={this.props.property.name}
                              label={this.props.property.label}
                              aria-label={this.props.property.label}
                              description={this.props.property.description}
                              isChecked={this.state.isChecked}
                              onChange={ (checked, _event) => {
                                  const newValue: string = checked ? "true" : "false";
                                  this.setSingleState("isChecked", checked);
                                  this.props.onChange(this.props.property, newValue);
                              }}
                    />
                </div>;
    }

    private renderStringProp(type: 'text' | 'number'): React.ReactElement {
        return <div className="configuration-property string-property">
            <div className="property-name">{ this.props.property.label }</div>
            <div className="property-description">{ this.props.property.description }</div>
            <div className="property-value">
                <PropertyInput name={ this.props.property.name }
                               value={ this.props.property.value }
                               type={ type } onChange={(newValue) => {
                    this.props.onChange(this.props.property, newValue);
                }} />
            </div>
        </div>;
    }
}
