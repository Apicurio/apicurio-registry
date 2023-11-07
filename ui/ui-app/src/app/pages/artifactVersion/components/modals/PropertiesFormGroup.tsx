import React, { FunctionComponent } from "react";
import { Button, FormGroup, GridItem, TextInput } from "@patternfly/react-core";
import { MinusCircleIcon, PlusCircleIcon } from "@patternfly/react-icons";

export type ArtifactProperty = {
    name: string;
    value: string | undefined;
    nameValidated: "success" | "warning" | "error" | "default";
    valueValidated: "success" | "warning" | "error" | "default";
}

/**
 * Properties
 */
export type PropertiesFormGroupProps = {
    properties: ArtifactProperty[];
    onChange: (properties: ArtifactProperty[]) => void;
};

export const PropertiesFormGroup: FunctionComponent<PropertiesFormGroupProps> = ({ properties, onChange }: PropertiesFormGroupProps) => {

    const addArtifactProperty = (): void => {
        const newProps: ArtifactProperty[] = [...properties, {
            name: "",
            value: "",
            nameValidated: "default",
            valueValidated: "default"
        }];
        onChange(newProps);
    };

    const removeProperty = (propertyToRemove: ArtifactProperty): void => {
        const newProps: ArtifactProperty[] = properties.filter(property => property !== propertyToRemove);
        onChange(newProps);
    };

    return (
        <React.Fragment>
            <GridItem span={12}>
                <label className="pf-c-form__label"><span className="pf-c-form__label-text">Properties</span></label>
            </GridItem>
            {
                properties.map((property, idx) => (
                    <React.Fragment key={idx}>
                        <FormGroup
                            fieldId={`form-properties-key-${idx}`}
                            isRequired={true}
                            label={idx === 0 ? "Key" : ""}>
                            <TextInput
                                type="text"
                                placeholder="Enter key"
                                id={`form-properties-key-${idx}`}
                                data-testid={`edit-metadata-modal-property-name-${idx}`}
                                name={`form-properties-key-${idx}`}
                                validated={property.nameValidated}
                                value={property.name}
                                onChange={(_event, newVal) => {
                                    property.name = newVal;
                                    onChange([...properties]);
                                }}
                            />
                        </FormGroup>
                        <FormGroup
                            fieldId={`form-properties-value-${idx}`}
                            label={idx === 0 ? "Value" : ""}
                        >
                            <div className="prop-value-group">
                                <TextInput
                                    type="text"
                                    id={`form-properties-value-${idx}`}
                                    data-testid={`edit-metadata-modal-property-value-${idx}`}
                                    placeholder="Enter value"
                                    name={`form-properties-value-${idx}`}
                                    validated={property.valueValidated}
                                    value={property.value}
                                    onChange={(_event, newVal) => {
                                        property.value = newVal;
                                        onChange([...properties]);
                                    }}
                                />
                                <Button key={"remove-button-new"} variant="link"
                                    icon={<MinusCircleIcon />} iconPosition="right"
                                    className="pf-m-plain" onClick={() => {
                                        removeProperty(property);
                                    }} />
                            </div>
                        </FormGroup>
                    </React.Fragment>
                ))
            }
            <GridItem span={12}>
                <Button
                    variant="link"
                    icon={<PlusCircleIcon />}
                    className="add-property-button"
                    data-testid="edit-metadata-modal-add-property"
                    onClick={() => addArtifactProperty()}
                >
                    Add property
                </Button>{" "}
            </GridItem>
        </React.Fragment>
    );
};
