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
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {Button, Form, FormGroup, Modal, TextArea, TextInput} from "@patternfly/react-core";
import {EditableMetaData} from "@apicurio/registry-services";


/**
 * Properties
 */
export interface EditMetaDataModalProps extends PureComponentProps {
    name: string;
    description: string;
    isOpen: boolean;
    onClose: () => void;
    onEditMetaData: (metaData: EditableMetaData) => void;
}

/**
 * State
 */
export interface EditMetaDataModalState extends PureComponentState {
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
                title="Edit Artifact Meta-Data"
                isLarge={true}
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="edit-artifact-metaData pf-m-redhat-font"
                actions={[
                    <Button key="edit" variant="primary" data-testid="modal-btn-edit" onClick={this.doEdit}>Edit</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.props.onClose}>Cancel</Button>
                ]}
            >
                <p>Use the form below to update the Name and Description of the artifact.</p>
                <Form>
                    <FormGroup
                        label="Name"
                        fieldId="form-name"
                        helperText="(Optional) Enter a custom name for the artifact"
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
                    <FormGroup
                        label="Description"
                        fieldId="form-description"
                        helperText="(Optional) Enter a custom description for the artifact"
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
                </Form>
            </Modal>
        );
    }

    public componentDidUpdate(prevProps: Readonly<EditMetaDataModalProps>): void {
        if (this.props.isOpen && !prevProps.isOpen) {
            this.setSingleState("metaData", {
                description: this.props.description,
                name: this.props.name
            });
        }
    }

    protected initializeState(): EditMetaDataModalState {
        return {
            metaData: {
                description: "",
                name: ""
            }
        };
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

    private onDescriptionChange = (value: string): void => {
        this.setSingleState("metaData", {
            ...this.state.metaData,
            description: value
        });
    };

}
