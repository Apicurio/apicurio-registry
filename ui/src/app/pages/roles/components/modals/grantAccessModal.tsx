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
import {Button, Form, FormGroup, Modal, TextInput} from '@patternfly/react-core';
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {RoleTypes} from "../../../../../models";

/**
 * Properties
 */
export interface GrantAccessModalProps extends PureComponentProps {
    isOpen: boolean;
    onClose: () => void;
    onGrant: (principalId: string, role: string) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface GrantAccessModalState extends PureComponentState {
    isValid: boolean;
    principalId: string;
    role: string;
}


/**
 * Models the modal dialog for granting access to a user.
 */
export class GrantAccessModal extends PureComponent<GrantAccessModalProps, GrantAccessModalState> {

    constructor(props: Readonly<GrantAccessModalProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Modal
                title="Grant Access"
                variant="medium"
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="grant-access-modal pf-m-redhat-font"
                actions={[
                    <Button key="grant" variant="primary" data-testid="modal-btn-grant" onClick={this.doGrantAccess} isDisabled={!this.state.isValid}>Grant</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.props.onClose}>Cancel</Button>
                ]}
            >

                <Form>
                    <FormGroup
                        label="Principal"
                        isRequired
                        fieldId="grant-access-principal"
                        helperText="Please provide a valid principal identifier"
                    >
                        <TextInput
                            isRequired
                            type="text"
                            id="grant-access-principal"
                            name="grant-access-principal"
                            aria-describedby="grant-access-principal-helper"
                            onChange={this.handlePrincipalChange}
                        />
                    </FormGroup>
                    <FormGroup
                        label="Role"
                        isRequired
                        fieldId="grant-access-role"
                    >
                        <select
                            className="pf-c-form-control pf-m-placeholder"
                            id="grant-access-role"
                            name="grant-access-role"
                            aria-label="Select a role"
                            value={ this.state.role }
                            onChange={this.handleRoleChange}
                        >
                            <option value={ RoleTypes.READ_ONLY }>Viewer</option>
                            <option value={ RoleTypes.DEVELOPER }>Developer</option>
                            <option value={ RoleTypes.ADMIN }>Admin</option>
                        </select>
                    </FormGroup>
                </Form>

            </Modal>
        );
    }

    protected initializeState(): GrantAccessModalState {
        return {
            isValid: false,
            principalId: "",
            role: RoleTypes.READ_ONLY
        };
    }

    public reset(): void {
        this.setMultiState(this.initializeState());
    }

    private handlePrincipalChange = (value: string): void => {
        this.setMultiState({
            principalId: value,
            isValid: this.checkValid(value, this.state.role)
        })
    };

    private handleRoleChange = (event: any): void => {
        this.setMultiState({
            role: event.target.value,
            isValid: this.checkValid(this.state.principalId, event.target.value)
        })
    };

    private doGrantAccess = (): void => {
        this.props.onGrant(this.state.principalId, this.state.role);
    }

    private checkValid(principalId: string, role: string): boolean {
        if (!principalId) {
            return false;
        }
        if (!role) {
            return false;
        }
        return true;
    }
}
