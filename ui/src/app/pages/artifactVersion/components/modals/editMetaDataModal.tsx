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
import React from 'react';
import "./editMetaDataModal.css";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { Button, Form, FormGroup, Grid, GridItem, Modal, TextArea, TextInput, TextInputProps } from "@patternfly/react-core";
import { MinusCircleIcon, PlusCircleIcon } from '@patternfly/react-icons';
import { EditableMetaData } from '../../../../../services';


/**
 * Properties
 */
export interface EditMetaDataModalProps extends PureComponentProps {
    name: string;
    description: string;
    labels: string[];
    properties: { [key: string]: string };
    isOpen: boolean;
    onClose: () => void;
    onEditMetaData: (metaData: EditableMetaData) => void;
}

type ValidatedValue = "error" | "default" | "warning" | "success" | undefined;

const initialFormState = {
    hasErrors: false,
    newPropertyKey: {
        value: "",
        validated: 'default' as ValidatedValue,
        errorMessage: ""
    },
    newArtifactPropertyValue: {
        value: "",
        validated: 'default' as ValidatedValue,
        errorMessage: ""
    }
};

/**
 * State
 */
export interface EditMetaDataModalState extends PureComponentState {
    labels: string;
    formState: {
        newPropertyKey: {
            value: string;
            errorMessage: string;
            validated: ValidatedValue;
        }
        newArtifactPropertyValue: {
            value: string;
            errorMessage: string;
            validated: ValidatedValue;
        }
        hasErrors: boolean;
    }
    metaData: EditableMetaData;
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
                title="Edit artifact metadata"
                variant="large"
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="edit-artifact-metaData pf-m-redhat-font"
                actions={[
                    <Button key="edit" variant="primary" data-testid="modal-btn-edit" onClick={this.doEdit} isDisabled={this.state.formState.hasErrors}>Edit</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.props.onClose}>Cancel</Button>
                ]}
            >
                <p>Use the form below to update the Name and Description of the artifact.</p>
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
                        {this.renderExistingArtifactPropertiesInForm()}
                        <FormGroup
                            fieldId="form-properties-key"
                            validated={this.state.formState.newPropertyKey.validated}
                            helperTextInvalid={this.state.formState.newPropertyKey.errorMessage}
                            label={Object.keys(this.state.metaData.properties).length == 0 ? 'Key' : ''}>
                            <TextInput
                                type="text"
                                placeholder='Enter key'
                                id="form-properties-key"
                                name="form-properties-key"
                                validated={this.state.formState.newPropertyKey.validated}
                                value={this.state.formState.newPropertyKey.value}
                                onChange={(newVal) => this.updateArtifactPropertyFormKey(newVal)}
                            />
                        </FormGroup>
                        <FormGroup
                            fieldId="form-properties-value"
                            label={Object.keys(this.state.metaData.properties).length == 0 ? 'Value' : ''}
                            validated={this.state.formState.newArtifactPropertyValue.validated}
                            helperTextInvalid={this.state.formState.newArtifactPropertyValue.errorMessage}
                        >
                            <div className='prop-value-group'>
                                <TextInput
                                    type="text"
                                    id="form-properties-value"
                                    placeholder="Enter value"
                                    name="form-properties-value"
                                    validated={this.state.formState.newArtifactPropertyValue.validated}
                                    value={this.state.formState.newArtifactPropertyValue.value}
                                    onChange={(newVal) => this.updateArtifactPropertyFormValue(newVal)}
                                />
                                <Button key={'remove-button-new'} variant="link" icon={<MinusCircleIcon />} iconPosition="right" className='pf-m-plain' isDisabled />
                            </div>
                        </FormGroup>
                        <GridItem span={12}>
                            <Button variant="link" icon={<PlusCircleIcon />} className="add-property-button" onClick={() => this.addArtifactProperty(this.state.formState.newPropertyKey.value, this.state.formState.newArtifactPropertyValue.value)}>
                                Add property
                            </Button>{' '}
                        </GridItem>
                    </Grid>
                </Form>
            </Modal>
        );
    }

    public componentDidUpdate(prevProps: Readonly<EditMetaDataModalProps>): void {
        if (this.props.isOpen && !prevProps.isOpen) {
            this.setMultiState({
                labels: this.props.labels.join(", "),
                metaData: {
                    description: this.props.description,
                    labels: this.props.labels,
                    properties: this.props.properties,
                    name: this.props.name
                }
            });
        }
    }

    protected initializeState(): EditMetaDataModalState {
        return {
            labels: "",
            formState: initialFormState,
            metaData: {
                description: "",
                labels: [],
                properties: {},
                name: ""
            }
        };
    }

    private labels(): string {
        if (this.state.metaData.labels) {
            return this.state.metaData.labels.join(", ");
        } else {
            return "";
        }
    }

    private doEdit = (): void => {
        this.props.onEditMetaData(this.state.metaData);
    };

    private onNameChange = (value: string): void => {
        this.setSingleState("metaData", {
            ...this.state.metaData,
            name: value
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
        });
    };

    private onDescriptionChange = (value: string): void => {
        this.setSingleState("metaData", {
            ...this.state.metaData,
            description: value
        });
    };

    private renderExistingArtifactPropertiesInForm = () => {
        const rows = Object.keys(this.state.metaData.properties).map((k: string, i: number) => {
            return <React.Fragment key={k}>
                <FormGroup fieldId={'form-properties-key' + k} label={i == 0 ? 'Key' : ''}>
                    <TextInput
                        type="text"
                        isDisabled
                        placeholder='Enter key'
                        id={'form-properties-key' + k}
                        name={'form-properties-key' + k}
                        value={k}
                    />
                </FormGroup>
                <FormGroup fieldId={'form-properties-value' + k} label={i == 0 ? 'Value' : ''}>
                    <div className='prop-value-group'>

                        <TextInput
                            type="text"
                            id={'form-properties-value' + k}
                            placeholder="Enter value"
                            name={'form-properties-value' + k}
                            value={this.state.metaData.properties[k]}
                            onChange={(newVal) => this.updateArtifactPropertyValue(k, newVal)}
                        />
                        <Button key={'remove-button-' + k} variant="link" icon={<MinusCircleIcon />} iconPosition="right" className='pf-m-plain' onClick={() => this.removeArtifactProperty(k)} />
                    </div>
                </FormGroup>
            </React.Fragment>
        });

        return rows;
    }

    /**
     * Update the form value for the artifact property key
     * @param key 
     */
    private updateArtifactPropertyFormKey(key: string) {
        let validated: ValidatedValue = 'default';
        let errorMessage: string = '';
        if (this.state.metaData.properties[key]) {
            errorMessage = `Key '${key}' is already in use`;
            validated = 'error';
        } else {
            errorMessage = '';
        }

        const propertyValueErrorData = this.getPropertyValueErrorInfo(this.state.formState.newArtifactPropertyValue.value, key);

        this.setMultiState({
            ...this.state,
            formState: {
                ...this.state.formState,
                hasErrors: errorMessage != '',
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

    /**
     * Update the form value for the artifact property value
     * @param value 
     */
    private updateArtifactPropertyFormValue(value: string = '') {
        const errorData = this.getPropertyValueErrorInfo(value, this.state.formState.newPropertyKey.value);

        this.setMultiState({
            ...this.state,
            formState: {
                ...this.state.formState,
                hasErrors: errorData.errorMessage != '',
                newArtifactPropertyValue: {
                    ...this.state.formState.newArtifactPropertyValue,
                    value,
                    errorMessage: errorData.errorMessage,
                    validated: errorData.validated,
                }
            }
        })
    }

    private updateArtifactPropertyValue(key: string, value: string) {
        const metadata: EditableMetaData = { ...this.state.metaData };
        metadata.properties[key] = value;
        this.setSingleState('metaData', metadata);
    }

    private removeArtifactProperty(key: string) {
        const metadata = Object.assign({}, this.state.metaData);
        delete metadata.properties[key];
        this.setSingleState('metaData', metadata);
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

    /**
     * Calculate the error values for the artifact property form value
     * @param value 
     * @param key 
     * @returns 
     */
    private getPropertyValueErrorInfo(value: string, key: string): { errorMessage: string, validated: ValidatedValue } {
        if (value === '' && key !== '') {
            return {
                errorMessage: `Key '${key}' must have a corresponding value`,
                validated: 'error'
            }
        }
        return {
            errorMessage: '',
            validated: 'default'
        }
    }
}