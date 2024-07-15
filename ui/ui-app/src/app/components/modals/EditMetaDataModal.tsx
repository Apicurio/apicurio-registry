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
import { Labels } from "@sdk/lib/generated-client/models";
import { labelsToList, listToLabels } from "@utils/labels.utils.ts";


export type MetaData = {
    name?: string;
    description?: string;
    labels?: Labels;
}


/**
 * Labels
 */
export type EditMetaDataModalProps = {
    entityType: string;
    name?: string;
    description?: string;
    labels?: Labels;
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
        if (props.name === undefined) {
            delete data.name;
        }
        if (props.description === undefined) {
            delete data.description;
        }
        if (props.labels === undefined) {
            delete data.labels;
        }
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
        if (props.labels !== undefined) {
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
        } else {
            setIsValid(true);
        }
    };

    useEffect(() => {
        validate();
    }, [name, description, labels]);

    useEffect(() => {
        if (props.isOpen) {
            if (props.labels !== undefined) {
                setLabels(labelsToList(props.labels));
            }
            if (props.name !== undefined) {
                setName(props.name);
            }
            if (props.description !== undefined) {
                setDescription(props.description);
            }
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

                    <If condition={props.description !== undefined}>
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
                    </If>

                    <If condition={props.labels !== undefined}>
                        <LabelsFormGroup labels={labels} onChange={onLabelsChange} />
                    </If>
                </Grid>
            </Form>
        </Modal>
    );
};
