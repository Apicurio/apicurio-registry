import React, { FunctionComponent, useState } from "react";
import "./ConfigProperty.css";
import { Button, Flex, FlexItem, Switch } from "@patternfly/react-core";
import { ConfigurationProperty } from "@models/configurationProperty.model.ts";
import { PropertyInput } from "@app/pages";
import { If } from "@app/components";
import { CheckIcon, CloseIcon, PencilAltIcon } from "@patternfly/react-icons";

/**
 * Properties
 */
export interface ConfigPropertyProps {
    property: ConfigurationProperty;
    onChange: (property: ConfigurationProperty, newValue: string) => void;
}


export const ConfigProperty: FunctionComponent<ConfigPropertyProps> = ({ property, onChange }: ConfigPropertyProps) => {
    const [ isEditing, setEditing ] = useState(false);
    const [ newPropertyValue, setNewPropertyValue ] = useState(property.value);
    const [ isValid, setValid ] = useState(true);

    const onCheckboxChange = (_event: any, checked: boolean): void => {
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
            <Flex className="configuration-property boolean-property" flexWrap={{ default: "nowrap" }}>
                <FlexItem grow={{ default: "grow" }}>
                    <div className="property-name">
                        <span className="name">{property.label}</span>
                        <span className="sep">:</span>
                        <span className="value">{property.value === "true" ? "On" : "Off"}</span>
                    </div>
                    <div className="property-description">{property.description}</div>
                </FlexItem>
                <FlexItem className="actions" align={{ default: "alignRight" }}>
                    <Switch id={property.name} aria-label={property.label}
                        className="action"
                        isChecked={property.value === "true"}
                        onChange={onCheckboxChange} />
                </FlexItem>
            </Flex>
        );
    };

    const renderStringProp = (type: "text" | "number"): React.ReactElement => {
        return (
            <Flex className="configuration-property string-property" flexWrap={{ default: "nowrap" }}>
                <FlexItem grow={{ default: "grow" }}>
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
                <FlexItem className="actions" align={{ default: "alignRight" }}>
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
    };

    if (property.type === "java.lang.Boolean") {
        return renderBooleanProp();
    } else if (property.type === "java.lang.Integer") {
        return renderStringProp("number");
    } else if (property.type === "java.lang.Long") {
        return renderStringProp("number");
    } else {
        return renderStringProp("text");
    }

};
