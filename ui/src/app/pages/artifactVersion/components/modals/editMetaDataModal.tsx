/**
 * @license
 * Copyright 2020 JBoss Inc
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
import "./editMetaDataModal.css";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { Button, Form, FormGroup, Grid, GridItem, Modal, TextArea, TextInput } from "@patternfly/react-core";
import { EditableMetaData } from "../../../../../services";
import { ArtifactProperty, listToProperties, PropertiesFormGroup, propertiesToList } from "./propertiesFormGroup";


/**
 * Properties
 */
export interface EditMetaDataModalProps extends PureComponentProps {
    name: string;
    description: string;
    labels: string[];
    properties: { [key: string]: string|undefined };
    isOpen: boolean;
    onClose: () => void;
    onEditMetaData: (metaData: EditableMetaData) => void;
}

/**
 * State
 */
export interface EditMetaDataModalState extends PureComponentState {
    labels: string;
    properties: ArtifactProperty[];
    metaData: EditableMetaData;
    isValid: boolean;
}

/**
 * Models the toolbar for the Artifacts page.
 */
export class EditMetaDataModal extends PureComponent<EditMetaDataModalProps, EditMetaDataModalState> {

    constructor(props: Readonly<EditMetaDataModalProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Modal
                title="Edit version metadata"
                variant="large"
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="edit-artifact-metaData pf-m-redhat-font"
                actions={[
                    <Button key="edit" variant="primary" data-testid="modal-btn-edit" onClick={this.doEdit} isDisabled={!this.state.isValid}>Save</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.props.onClose}>Cancel</Button>
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
                                    value={this.state.metaData.name}
                                    placeholder="Name of the artifact"
                                    onChange={this.onNameChange}
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
                                    value={this.state.metaData.description}
                                    placeholder="Description of the artifact"
                                    onChange={this.onDescriptionChange}
                                />
                            </FormGroup>
                        </GridItem>

                        <GridItem span={12}>
                            <FormGroup
                                label="Labels"
                                fieldId="form-labels"
                                helperText="A comma-separated list of labels to apply to the artifact."
                            >
                                <TextInput
                                    isRequired={false}
                                    type="text"
                                    id="form-labels"
                                    data-testid="form-labels"
                                    name="form-labels"
                                    aria-describedby="form-labels-helper"
                                    value={this.state.labels}
                                    placeholder="Artifact labels"
                                    onChange={this.onLabelsChange}
                                />
                            </FormGroup>
                        </GridItem>
                        <PropertiesFormGroup properties={this.state.properties}
                                             onChange={this.onPropertiesChange} />
                    </Grid>
                </Form>
            </Modal>
        );
    }

    public componentDidUpdate(prevProps: Readonly<EditMetaDataModalProps>): void {
        if (this.props.isOpen && !prevProps.isOpen) {
            this.setMultiState({
                labels: this.props.labels.join(", "),
                properties: propertiesToList(this.props.properties),
                metaData: {
                    description: this.props.description,
                    labels: this.props.labels,
                    properties: this.props.properties,
                    name: this.props.name
                },
                isValid: true
            });
        }
    }

    protected initializeState(): EditMetaDataModalState {
        return {
            labels: "",
            properties: [],
            isValid: true,
            metaData: {
                description: "",
                labels: [],
                properties: {},
                name: ""
            }
        };
    }

    private doEdit = (): void => {
        const metaData: EditableMetaData = {
            ...this.state.metaData,
            properties: listToProperties(this.state.properties)
        }
        this.props.onEditMetaData(metaData);
    };

    private onNameChange = (value: string): void => {
        this.setSingleState("metaData", {
            ...this.state.metaData,
            name: value
        }, () => {
            this.validate();
        });
    };

    private onLabelsChange = (value: string): void => {
        let labels: string[] = [];
        if (value && value.trim().length > 0) {
            labels = value.trim().split(",").map(item => item.trim());
        }
        this.setMultiState({
            labels: value,
            metaData: {
                ...this.state.metaData,
                labels
            }
        }, () => {
            this.validate();
        });
    };

    private onDescriptionChange = (value: string): void => {
        this.setSingleState("metaData", {
            ...this.state.metaData,
            description: value
        }, () => {
            this.validate();
        });
    };

    private onPropertiesChange = (properties: ArtifactProperty[]): void => {
        this.setSingleState("properties", properties, () => {
            this.validate();
        });
    };

    private validate = (): void => {
        const properties: ArtifactProperty[] = [...this.state.properties];
        let isValid: boolean = true;
        if (properties) {
            let propertyKeys: string[] = [];
            properties.forEach(property => {
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
        this.setMultiState({
            isValid,
            properties
        });
    };

}
