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
import React, { FunctionComponent, useState } from "react";
import "./configProperty.css";
import { Button, Flex, FlexItem, Switch } from "@patternfly/react-core";
import { ConfigurationProperty } from "../../../../models/configurationProperty.model";
import { PropertyInput } from "./propertyInput";
import { If } from "../../../components/common/if";
import { CheckIcon, CloseIcon, PencilAltIcon } from "@patternfly/react-icons";

/**
 * Properties
 */
export interface ConfigPropertyProps {
    property: ConfigurationProperty;
    onChange: (property: ConfigurationProperty, newValue: string) => void;
    onReset: (property: ConfigurationProperty) => void;
}


export const ConfigProperty: FunctionComponent<ConfigPropertyProps> = ({property, onChange, onReset}: ConfigPropertyProps) => {
    const [ isEditing, setEditing ] = useState(false);
    const [ newPropertyValue, setNewPropertyValue ] = useState(property.value);
    const [ isValid, setValid ] = useState(true);

    const onCheckboxChange = (checked: boolean): void => {
        const newValue: string = checked ? "true" : "false";
        onChange(property, newValue);
    };

    const onPropertyValueChange = (newValue: string): void => {
        setNewPropertyValue(newValue);
    };

    const onPropertyValueValid = (valid: boolean): void => {
        setValid(valid);
    };

    const onCancelEdit = (): void => {
        setNewPropertyValue(property.value);
        setEditing(false);
    };

    const onStartEditing = (): void => {
        setValid(true);
        setEditing(true);
    };

    const onSavePropertyValue = (): void => {
        onChange(property, newPropertyValue);
        setEditing(false);
    };

    const renderBooleanProp = (): React.ReactElement => {
        return (
            <Flex className="configuration-property boolean-property" flexWrap={{default: "nowrap"}}>
                <FlexItem grow={{default: "grow"}}>
                    <div className="property-name">
                        <span className="name">{property.label}</span>
                        <span className="sep">:</span>
                        <span className="value">{property.value === "true" ? "On" : "Off"}</span>
                    </div>
                    <div className="property-description">{property.description}</div>
                </FlexItem>
                <FlexItem className="actions" align={{default: "alignRight"}}>
                    <Switch id={property.name} aria-label={property.label}
                            className="action"
                            isChecked={property.value === "true"}
                            onChange={onCheckboxChange} />
                </FlexItem>
            </Flex>
        );
    };

    const renderStringProp = (type: 'text' | 'number'): React.ReactElement => {
        return (
            <Flex className="configuration-property string-property" flexWrap={{default: "nowrap"}}>
                <FlexItem grow={{default: "grow"}}>
                    <div className="property-name">
                        <span className="name">{property.label}</span>
                    </div>
                    <div className="property-description">{property.description}</div>
                    <If condition={!isEditing}>
                        <div className="property-value">{property.value}</div>
                    </If>
                    <If condition={isEditing}>
                        <div className="property-editor">
                            <PropertyInput name={ property.name }
                                           value={ property.value }
                                           type={ type }
                                           onChange={ onPropertyValueChange }
                                           onValid={ onPropertyValueValid }
                                           onCancel={ onCancelEdit }
                                           onSave={ onSavePropertyValue }
                            />
                        </div>
                    </If>
                </FlexItem>
                <FlexItem className="actions" align={{default: "alignRight"}}>
                    <If condition={!isEditing}>
                        <Button variant="plain" className="action single" onClick={onStartEditing}><PencilAltIcon /></Button>
                    </If>
                    <If condition={isEditing}>
                        <Button variant="plain" className="action" onClick={onSavePropertyValue} isDisabled={!isValid}><CheckIcon /></Button>
                        <Button variant="plain" className="action" onClick={onCancelEdit}><CloseIcon /></Button>
                    </If>
                </FlexItem>
            </Flex>
        );
    }

    if (property.type === "java.lang.Boolean") {
        return renderBooleanProp();
    } else if (property.type === "java.lang.Integer") {
        return renderStringProp("number");
    } else if (property.type === "java.lang.Long") {
        return renderStringProp("number");
    } else {
        return renderStringProp("text");
    }

}
