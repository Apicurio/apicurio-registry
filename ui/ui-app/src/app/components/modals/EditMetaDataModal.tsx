import { FunctionComponent, useEffect, useState } from "react";
import "./EditMetaDataModal.css";
import {
    Button,
    Form,
    FormGroup,
    Grid,
    GridItem,
    Modal,
    TextArea,
    TextInput
} from "@patternfly/react-core";
import { If } from "@apicurio/common-ui-components";
import { ArtifactLabel, LabelsFormGroup } from "@app/components";


export type MetaData = {
    name?: string;
    description: string;
    labels: { [key: string]: string|undefined };
}


function labelsToList(labels: { [key: string]: string|undefined }): ArtifactLabel[] {
    return Object.keys(labels).filter((key) => key !== undefined).map(key => {
        return {
            name: key,
            value: labels[key],
            nameValidated: "default",
            valueValidated: "default"
        };
    });
}

function listToLabels(labels: ArtifactLabel[]): { [key: string]: string|undefined } {
    const rval: { [key: string]: string|undefined } = {};
    labels.forEach(label => {
        if (label.name) {
            rval[label.name] = label.value;
        }
    });
    return rval;
}


/**
 * Labels
 */
export type EditMetaDataModalProps = {
    entityType: string;
    name?: string;
    description: string;
    labels: { [key: string]: string|undefined };
    isOpen: boolean;
    onClose: () => void;
    onEditMetaData: (metaData: MetaData) => void;
};

/**
 * Models the edit meta data dialog.
 */
export const EditMetaDataModal: FunctionComponent<EditMetaDataModalProps> = (props: EditMetaDataModalProps) => {
    const [name, setName] = useState<string|undefined>("");
    const [description, setDescription] = useState("");
    const [labels, setLabels] = useState<ArtifactLabel[]>([]);
    const [isValid, setIsValid] = useState(true);

    const doEdit = (): void => {
        const data: MetaData = {
            name,
            description,
            labels: listToLabels(labels)
        };
        props.onEditMetaData(data);
    };

    const onNameChange = (_event: any, value: string): void => {
        setName(value);
    };

    const onDescriptionChange = (_event: any, value: string): void => {
        setDescription(value);
    };

    const onLabelsChange = (labels: ArtifactLabel[]): void => {
        setLabels(labels);
    };

    const validate = (): void => {
        const labelsClone: ArtifactLabel[] = [...labels];
        let isValid: boolean = true;
        if (labelsClone) {
            const labelKeys: string[] = [];
            labelsClone.forEach(label => {
                label.nameValidated = "default";
                if ((label.name === "" || label.name === undefined) && label.value !== "") {
                    label.nameValidated = "error";
                    isValid = false;
                } else if (label.name !== "" && label.name !== undefined) {
                    if (labelKeys.includes(label.name)) {
                        label.nameValidated = "error";
                        isValid = false;
                    }
                    labelKeys.push(label.name);
                }
            });
        }
        setIsValid(isValid);
        setLabels(labels);
    };

    useEffect(() => {
        validate();
    }, [name, description, labels]);

    useEffect(() => {
        if (props.isOpen) {
            setLabels(labelsToList(props.labels));
            setName(props.name);
            setDescription(props.description);
            setIsValid(true);
        }
    }, [props.isOpen]);

    return (
        <Modal
            title={`Edit ${props.entityType} meta data`}
            variant="large"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="edit-metaData pf-m-redhat-font"
            actions={[
                <Button key="edit" variant="primary" data-testid="modal-btn-edit" onClick={doEdit} isDisabled={!isValid}>Save</Button>,
                <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={props.onClose}>Cancel</Button>
            ]}
        >
            <Form>
                <Grid hasGutter md={6}>
                    <If condition={props.name !== undefined}>
                        <GridItem span={12}>
                            <FormGroup
                                label="Name"
                                fieldId="form-name"
                            >
                                <TextInput
                                    isRequired={false}
                                    type="text"
                                    id="form-name"
                                    data-testid="edit-metadata-modal-name"
                                    name="form-name"
                                    aria-describedby="form-name-helper"
                                    value={name}
                                    placeholder={`Name of the ${props.entityType}`}
                                    onChange={onNameChange}
                                />
                            </FormGroup>
                        </GridItem>
                    </If>

                    <GridItem span={12}>
                        <FormGroup
                            label="Description"
                            fieldId="form-description"
                        >
                            <TextArea
                                isRequired={false}
                                id="form-description"
                                data-testid="edit-metadata-modal-description"
                                name="form-description"
                                aria-describedby="form-description-helper"
                                value={description}
                                placeholder={`Description of the ${props.entityType}`}
                                onChange={onDescriptionChange}
                            />
                        </FormGroup>
                    </GridItem>
                    <LabelsFormGroup labels={labels} onChange={onLabelsChange} />
                </Grid>
            </Form>
        </Modal>
    );
};
