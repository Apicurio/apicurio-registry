import React, { FunctionComponent } from "react";
import { Button, FormGroup, GridItem, TextInput } from "@patternfly/react-core";
import { MinusCircleIcon, PlusCircleIcon } from "@patternfly/react-icons";

export type ArtifactLabel = {
    name: string;
    value: string | undefined;
    nameValidated: "success" | "warning" | "error" | "default";
    valueValidated: "success" | "warning" | "error" | "default";
}

/**
 * Labels
 */
export type LabelsFormGroupProps = {
    labels: ArtifactLabel[];
    onChange: (labels: ArtifactLabel[]) => void;
};

export const LabelsFormGroup: FunctionComponent<LabelsFormGroupProps> = ({ labels, onChange }: LabelsFormGroupProps) => {

    const addArtifactLabel = (): void => {
        const newProps: ArtifactLabel[] = [...labels, {
            name: "",
            value: "",
            nameValidated: "default",
            valueValidated: "default"
        }];
        onChange(newProps);
    };

    const removeLabel = (labelToRemove: ArtifactLabel): void => {
        const newProps: ArtifactLabel[] = labels.filter(label => label !== labelToRemove);
        onChange(newProps);
    };

    return (
        <React.Fragment>
            <GridItem span={12}>
                <FormGroup label="Labels"></FormGroup>
            </GridItem>
            {
                labels.map((label, idx) => (
                    <React.Fragment key={idx}>
                        <FormGroup
                            fieldId={`form-labels-key-${idx}`}
                            isRequired={true}
                            label={idx === 0 ? "Key" : ""}>
                            <TextInput
                                type="text"
                                placeholder="Enter key"
                                id={`form-labels-key-${idx}`}
                                data-testid={`edit-metadata-modal-label-name-${idx}`}
                                name={`form-labels-key-${idx}`}
                                validated={label.nameValidated}
                                value={label.name}
                                onChange={(_event, newVal) => {
                                    label.name = newVal;
                                    onChange([...labels]);
                                }}
                            />
                        </FormGroup>
                        <FormGroup
                            fieldId={`form-labels-value-${idx}`}
                            label={idx === 0 ? "Value" : ""}
                        >
                            <div className="prop-value-group">
                                <TextInput
                                    type="text"
                                    id={`form-labels-value-${idx}`}
                                    data-testid={`edit-metadata-modal-label-value-${idx}`}
                                    placeholder="Enter value"
                                    name={`form-labels-value-${idx}`}
                                    validated={label.valueValidated}
                                    value={label.value}
                                    onChange={(_event, newVal) => {
                                        label.value = newVal;
                                        onChange([...labels]);
                                    }}
                                />
                                <Button key={"remove-button-new"} variant="link"
                                    icon={<MinusCircleIcon />} iconPosition="right"
                                    className="pf-m-plain" onClick={() => {
                                        removeLabel(label);
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
                    className="add-label-button"
                    data-testid="edit-metadata-modal-add-label"
                    onClick={() => addArtifactLabel()}
                >
                    Add label
                </Button>{" "}
            </GridItem>
        </React.Fragment>
    );
};
