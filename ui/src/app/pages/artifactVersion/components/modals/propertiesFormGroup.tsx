import React, {FunctionComponent} from "react";
import {Button, FormGroup, GridItem, TextInput} from "@patternfly/react-core";
import {MinusCircleIcon, PlusCircleIcon} from "@patternfly/react-icons";

export type ArtifactProperty = {
    name: string;
    value: string | undefined;
}

/**
 * Properties
 */
export type PropertiesFormGroupProps = {
    properties: ArtifactProperty[];
    onChange: (properties: ArtifactProperty[]) => void;
};


export function propertiesToList(properties: { [key: string]: string|undefined }): ArtifactProperty[] {
    let rval: ArtifactProperty[] = Object.keys(properties).sort().map(key => {
        return {
            name: key,
            value: properties[key]
        };
    });
    return rval;
}

export function listToProperties(properties: ArtifactProperty[]): { [key: string]: string|undefined } {
    let rval: { [key: string]: string|undefined } = {};
    properties.forEach(property => {
        rval[property.name] = property.value;
    });
    return rval;
}


/**
         private renderExistingArtifactPropertiesInForm = () => {
                const rows = Object.keys(this.state.metaData.properties).map((k: string, i: number) => {
                    return <React.Fragment key={k}>
                        <FormGroup isRequired={true} fieldId={"form-properties-key" + k} label={i == 0 ? "Key" : ""}>
                            <TextInput
                                type="text"
                                placeholder="Enter key"
                                id={"form-properties-key" + k}
                                name={"form-properties-key" + k}
                                value={k}
                                onChange={newVal => this.updateArtifactPropertyKey(k, newVal)}
                            />
                        </FormGroup>
                        <FormGroup fieldId={"form-properties-value" + k} label={i == 0 ? "Value" : ""}>
                            <div className="prop-value-group">
                                <TextInput
                                    type="text"
                                    id={"form-properties-value" + k}
                                    placeholder="Enter value"
                                    name={"form-properties-value" + k}
                                    value={this.state.metaData.properties[k]}
                                    onChange={(newVal) => this.updateArtifactPropertyValue(k, newVal)}
                                />
                                <Button key={"remove-button-" + k} variant="link" icon={<MinusCircleIcon />} iconPosition="right" className="pf-m-plain" onClick={() => this.removeArtifactProperty(k)} />
                            </div>
                        </FormGroup>
                    </React.Fragment>
                });

                return rows;
            }

        private updateArtifactPropertyFormKey(key: string) {
            let validated: ValidatedValue = "default";
            let errorMessage: string = "";
            if (this.state.metaData.properties[key]) {
                errorMessage = `Key "${key}" is already in use`;
                validated = "error";
            } else {
                errorMessage = "";
            }

            const propertyValueErrorData = this.getPropertyValueErrorInfo(this.state.formState.newArtifactPropertyValue.value, key);

            this.setMultiState({
                ...this.state,
                formState: {
                    ...this.state.formState,
                    hasErrors: errorMessage != "",
                    newPropertyKey: {
                        ...this.state.formState.newPropertyKey,
                        errorMessage: errorMessage,
                        value: key,
                        validated,
                    },
                    newArtifactPropertyValue: {
                        value: this.state.formState.newArtifactPropertyValue.value,
                        errorMessage: propertyValueErrorData.errorMessage,
                        validated: propertyValueErrorData.validated
                    }
                }
            })
        }

        private updateArtifactPropertyFormValue(value: string = "") {
            const errorData = this.getPropertyValueErrorInfo(value, this.state.formState.newPropertyKey.value);

            this.setMultiState({
                ...this.state,
                formState: {
                    ...this.state.formState,
                    hasErrors: errorData.errorMessage != "",
                    newArtifactPropertyValue: {
                        ...this.state.formState.newArtifactPropertyValue,
                        value,
                        errorMessage: errorData.errorMessage,
                        validated: errorData.validated,
                    }
                }
            })
        }

        private updateArtifactPropertyKey(key: string, value: string) {
            const metadata: EditableMetaData = { ...this.state.metaData };
            metadata.properties[key] = value;
            this.setSingleState("metaData", metadata);
        }

        private updateArtifactPropertyValue(key: string, value: string) {
            const metadata: EditableMetaData = { ...this.state.metaData };
            metadata.properties[key] = value;
            this.setSingleState("metaData", metadata);
        }

        private removeArtifactProperty(key: string) {
            const metadata = Object.assign({}, this.state.metaData);
            delete metadata.properties[key];
            this.setSingleState("metaData", metadata);
        }

        private addArtifactProperty(key: string, value: string) {
            const metadata = Object.assign({}, this.state.metaData);
            metadata.properties[key] = value;
            this.setMultiState({
                ...this.state,
                metaData: metadata,
                formState: initialFormState,
            })
        }

        private getPropertyValueErrorInfo(value: string, key: string): { errorMessage: string, validated: ValidatedValue } {
            if (value === "" && key !== "") {
                return {
                    errorMessage: `Key "${key}" must have a corresponding value`,
                    validated: "error"
                }
            }
            return {
                errorMessage: "",
                validated: "default"
            }
        }
 */



export const PropertiesFormGroup: FunctionComponent<PropertiesFormGroupProps> = ({properties, onChange}: PropertiesFormGroupProps) => {

    const addArtifactProperty = (): void => {
        const newProps: ArtifactProperty[] = [...properties, {
            name: "",
            value: ""
        }];
        onChange(newProps);
    };

    const removeProperty = (propertyToRemove: ArtifactProperty): void => {
        const newProps: ArtifactProperty[] = properties.filter(property => property !== propertyToRemove);
        onChange(newProps);
    };

    return (
        <React.Fragment>
            {
                properties.map((property, idx) => (
                    <React.Fragment>
                        <FormGroup
                            fieldId="form-properties-key"
                            validated={"default"}
                            helperTextInvalid={""}
                            label={idx === 0 ? "Key" : ""}>
                            <TextInput
                                type="text"
                                placeholder="Enter key"
                                id="form-properties-key"
                                name="form-properties-key"
                                validated={"default"}
                                value={property.name}
                                onChange={(newVal) => {
                                    property.name = newVal;
                                    onChange([...properties]);
                                }}
                            />
                        </FormGroup>
                        <FormGroup
                            fieldId="form-properties-value"
                            label={idx === 0 ? "Value" : ""}
                            validated={"default"}
                            helperTextInvalid={""}>
                            <div className="prop-value-group">
                                <TextInput
                                    type="text"
                                    id="form-properties-value"
                                    placeholder="Enter value"
                                    name="form-properties-value"
                                    validated={"default"}
                                    value={property.value}
                                    onChange={(newVal) => {
                                        property.value = newVal;
                                        onChange([...properties]);
                                    }}
                                />
                                <Button key={"remove-button-new"} variant="link"
                                        icon={<MinusCircleIcon />} iconPosition="right"
                                        className="pf-m-plain" onClick={() => {
                                            removeProperty(property)
                                        }} />
                            </div>
                        </FormGroup>
                    </React.Fragment>
                ))
            }
            <GridItem span={12}>
                <Button variant="link" icon={<PlusCircleIcon />} className="add-property-button" onClick={() => addArtifactProperty()}>
                    Add property
                </Button>{" "}
            </GridItem>
        </React.Fragment>
    );
};
