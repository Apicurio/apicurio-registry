import { FunctionComponent, useEffect, useState } from "react";
import "./EditMetaDataModal.css";
import {
    Button,
    Form,
    FormGroup,
    FormHelperText,
    Grid,
    GridItem,
    HelperText,
    HelperTextItem,
    Modal,
    TextArea,
    TextInput
} from "@patternfly/react-core";
import { EditableMetaData } from "@services/groups";
import { ArtifactProperty, listToProperties, PropertiesFormGroup, propertiesToList } from "@app/pages";


/**
 * Properties
 */
export type EditMetaDataModalProps = {
    name: string;
    description: string;
    labels: string[];
    properties: { [key: string]: string|undefined };
    isOpen: boolean;
    onClose: () => void;
    onEditMetaData: (metaData: EditableMetaData) => void;
};

/**
 * Models the edit meta data dialog.
 */
export const EditMetaDataModal: FunctionComponent<EditMetaDataModalProps> = (props: EditMetaDataModalProps) => {
    const [labels, setLabels] = useState("");
    const [properties, setProperties] = useState<ArtifactProperty[]>([]);
    const [isValid, setIsValid] = useState(true);
    const [metaData, setMetaData] = useState<EditableMetaData>({
        description: "",
        labels: [],
        properties: {},
        name: ""
    });


    const doEdit = (): void => {
        const newMetaData: EditableMetaData = {
            ...metaData,
            properties: listToProperties(properties)
        };
        props.onEditMetaData(newMetaData);
    };

    const onNameChange = (_event: any, value: string): void => {
        setMetaData({
            ...metaData,
            name: value
        });
    };

    const onLabelsChange = (_event: any, value: string): void => {
        let labels: string[] = [];
        if (value && value.trim().length > 0) {
            labels = value.trim().split(",").map(item => item.trim());
        }
        setLabels(value);
        setMetaData({
            ...metaData,
            labels
        });
    };

    const onDescriptionChange = (_event: any, value: string): void => {
        setMetaData({
            ...metaData,
            description: value
        });
    };

    const onPropertiesChange = (properties: ArtifactProperty[]): void => {
        setProperties(properties);
    };

    const validate = (): void => {
        const propertiesClone: ArtifactProperty[] = [...properties];
        let isValid: boolean = true;
        if (propertiesClone) {
            const propertyKeys: string[] = [];
            propertiesClone.forEach(property => {
                property.nameValidated = "default";
                if ((property.name === "" || property.name === undefined) && property.value !== "") {
                    property.nameValidated = "error";
                    isValid = false;
                } else if (property.name !== "" && property.name !== undefined) {
                    if (propertyKeys.includes(property.name)) {
                        property.nameValidated = "error";
                        isValid = false;
                    }
                    propertyKeys.push(property.name);
                }
            });
        }
        setIsValid(isValid);
        setProperties(properties);
    };

    useEffect(() => {
        validate();
    }, [properties, metaData]);

    useEffect(() => {
        if (props.isOpen) {
            setLabels(props.labels.join(", "));
            setProperties(propertiesToList(props.properties));
            setMetaData({
                description: props.description,
                labels: props.labels,
                properties: props.properties,
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
                                data-testid="form-name"
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
                                data-testid="form-description"
                                name="form-description"
                                aria-describedby="form-description-helper"
                                value={metaData.description}
                                placeholder="Description of the artifact"
                                onChange={onDescriptionChange}
                            />
                        </FormGroup>
                    </GridItem>

                    <GridItem span={12}>
                        <FormGroup
                            label="Labels"
                            fieldId="form-labels"
                        >
                            <TextInput
                                isRequired={false}
                                type="text"
                                id="form-labels"
                                data-testid="form-labels"
                                name="form-labels"
                                aria-describedby="form-labels-helper"
                                value={labels}
                                placeholder="Artifact labels"
                                onChange={onLabelsChange}
                            />
                            <FormHelperText>
                                <HelperText>
                                    <HelperTextItem>
                                        A comma-separated list of labels to apply to the artifact.
                                    </HelperTextItem>
                                </HelperText>
                            </FormHelperText>
                        </FormGroup>
                    </GridItem>
                    <PropertiesFormGroup properties={properties}
                        onChange={onPropertiesChange} />
                </Grid>
            </Form>
        </Modal>
    );
};
