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
import { ArtifactLabel, listToLabels, LabelsFormGroup, labelsToList } from "@app/pages";
import { EditableMetaData } from "@services/useGroupsService.ts";


/**
 * Labels
 */
export type EditMetaDataModalProps = {
    name: string;
    description: string;
    labels: { [key: string]: string|undefined };
    isOpen: boolean;
    onClose: () => void;
    onEditMetaData: (metaData: EditableMetaData) => void;
};

/**
 * Models the edit meta data dialog.
 */
export const EditMetaDataModal: FunctionComponent<EditMetaDataModalProps> = (props: EditMetaDataModalProps) => {
    const [labels, setLabels] = useState<ArtifactLabel[]>([]);
    const [isValid, setIsValid] = useState(true);
    const [metaData, setMetaData] = useState<EditableMetaData>({
        description: "",
        labels: {},
        name: ""
    });


    const doEdit = (): void => {
        const newMetaData: EditableMetaData = {
            ...metaData,
            labels: listToLabels(labels)
        };
        props.onEditMetaData(newMetaData);
    };

    const onNameChange = (_event: any, value: string): void => {
        setMetaData({
            ...metaData,
            name: value
        });
    };

    const onDescriptionChange = (_event: any, value: string): void => {
        setMetaData({
            ...metaData,
            description: value
        });
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
    }, [labels, metaData]);

    useEffect(() => {
        if (props.isOpen) {
            setLabels(labelsToList(props.labels));
            setMetaData({
                description: props.description,
                labels: props.labels,
                name: props.name
            });
            setIsValid(true);
        }
    }, [props.isOpen]);

    return (
        <Modal
            title="Edit version metadata"
            variant="large"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="edit-artifact-metaData pf-m-redhat-font"
            actions={[
                <Button key="edit" variant="primary" data-testid="modal-btn-edit" onClick={doEdit} isDisabled={!isValid}>Save</Button>,
                <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={props.onClose}>Cancel</Button>
            ]}
        >
            <Form>
                <Grid hasGutter md={6}>
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
                                value={metaData.name}
                                placeholder="Name of the artifact"
                                onChange={onNameChange}
                            />
                        </FormGroup>
                    </GridItem>

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
                                value={metaData.description}
                                placeholder="Description of the artifact"
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
