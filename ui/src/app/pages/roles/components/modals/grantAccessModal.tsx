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
import { Button, 
    DescriptionList, 
    DescriptionListGroup, 
    DescriptionListTerm, 
    DescriptionListDescription, 
    Form, 
    FormGroup, 
    Modal,
    Radio, 
    Select, SelectOption, SelectOptionObject, SelectVariant,
    TextInput,
    Tooltip } from '@patternfly/react-core';
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { RoleTypes, RoleMapping } from "../../../../../models";
import {OutlinedQuestionCircleIcon} from '@patternfly/react-icons'
import "./grantAccessModal.css";

/**
 * Properties
 */
export interface GrantAccessModalProps extends PureComponentProps {
    isOpen: boolean;
    isUpdateAccess: boolean;
    serviceRegistryInstance?: string;
    accountId?: string;
    roles: null | RoleMapping[];
    defaultRole?: RoleMapping;
    onClose: () => void;
    onGrant: (accountId: string, role: string, isUpdate: boolean) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface GrantAccessModalState extends PureComponentState {
    isAccountIDSelectOpen: boolean;
    isValid: boolean;
    accountId: string | undefined;
    role: string | undefined;
}


/**
 * Models the modal dialog for granting access to a user.
 */
export class GrantAccessModal extends PureComponent<GrantAccessModalProps, GrantAccessModalState> {

    constructor(props: Readonly<GrantAccessModalProps>) {
        super(props);
    }
    componentDidUpdate(prevProps: GrantAccessModalProps) {
        if (this.props.defaultRole !== prevProps.defaultRole) {
            if (this.props.defaultRole) {
                this.setMultiState({
                    accountId: this.props.defaultRole.principalId,
                    role: this.props.defaultRole.role
                })
            } else {
                this.setMultiState({
                    accountId: "",
                    role: undefined
                })
            }
        }
        // Typical usage (don't forget to compare props):
        // if (this.props.userID !== prevProps.userID) {
        //   this.fetchData(this.props.userID);
        // }
      }

    public render(): React.ReactElement {
        return (
            <Modal
                title="Manage Permissions"
                description="Manage access to resources in this Service Registry instance by assigning permissions to an account."
                variant="medium"
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="grant-access-modal pf-m-redhat-font"
                actions={[
                    <Button key="grant" variant="primary" data-testid="modal-btn-grant" onClick={this.doGrantAccess} isDisabled={!this.state.isValid}>{this.props.isUpdateAccess ? "Update" : "Create"}</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.props.onClose}>Cancel</Button>
                ]}
            >


                <Form>
                    {this.props.serviceRegistryInstance !== undefined ? (<DescriptionList>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Service Registry instance</DescriptionListTerm>
                            <DescriptionListDescription>{this.props.serviceRegistryInstance}</DescriptionListDescription>
                        </DescriptionListGroup>
                    </DescriptionList>
                    ) : undefined}

                    <FormGroup
                        label="Account ID"
                        labelIcon={<Tooltip
                            position="top"
                            content={
                              <div>A service account enables your application or tool to connect securely to your resources. A user account enables users in your organization to access resources.</div>
                            }
                          >
                            <OutlinedQuestionCircleIcon/>
                          </Tooltip>}
                        isRequired
                        fieldId="grant-access-account-id"
                    >
                        {this.props.roles !== null ?
                        <Select
                            id="grant-access-principal"
                            name="grant-access-principal"
                            variant={SelectVariant.typeahead}
                            typeAheadAriaLabel="Select an account ID"
                            onToggle={this.onAccountIDToggle}
                            onSelect={this.onAccountIDSelect}
                            onClear={this.onAccountIDClearSelection}
                            selections={this.state.accountId}
                            isOpen={this.state.isAccountIDSelectOpen}
                            isInputValuePersisted={true}
                            placeholderText={this.props.isUpdateAccess ? this.props.defaultRole?.principalId : "Select an account ID"}
                            maxHeight = {'100px'}
                            isDisabled={this.props.isUpdateAccess}
                        >
                            {this.props.roles.map((option, index) => (
                                <SelectOption
                                    key={index}
                                    value={option.principalId}
                                    
                                />
                            ))}
                        </Select> :   
                           <TextInput
                           isRequired
                           type="text"
                           id="grant-access-principal"
                           name="grant-access-principal"
                           aria-describedby="grant-access-principal-helper"
                           onChange={this.handlePrincipalChange}
                       /> }
                    </FormGroup>
                    <FormGroup
                        label="Role"
                        isRequired
                        fieldId="grant-access-role"
                    >
                        <Radio id="grant-access-role"
                            className = "grant-access-radio-button"
                            name="grant-access-role"
                            label="Administrator" 
                            description="Give roles to other principals on this Service Registry instance, configure global rules, and access data import and export features."
                            value={RoleTypes.ADMIN}
                            onChange={this.handleRoleChange}
                            isChecked={this.state.role == RoleTypes.ADMIN}
                        />

                        <Radio id="grant-access-role"
                            className = "grant-access-radio-button"
                            name="grant-access-role"
                            label="Manager" 
                            description="Read and write artifacts on this Service Registry instance."
                            value={RoleTypes.DEVELOPER}
                            onChange={this.handleRoleChange}
                            isChecked={this.state.role == RoleTypes.DEVELOPER} />

                        <Radio id="grant-access-role"
                            className = "grant-access-radio-button"
                            name="grant-access-role"
                            label="Viewer" 
                            description="Read artifacts on this Service Registry instance."
                            value={RoleTypes.READ_ONLY}
                            onChange={this.handleRoleChange} 
                            isChecked={this.state.role == RoleTypes.READ_ONLY}/>
                    </FormGroup>
                </Form>

            </Modal>
        );
    }

    protected initializeState(): GrantAccessModalState {
        return {
            isAccountIDSelectOpen: false,
            isValid: false,
            accountId: this.props.isUpdateAccess && this.props.roles ? this.props.roles[0].principalId : "",
            role: undefined
        };
    }

    public reset(): void {
        this.setMultiState(this.initializeState());
    }

    private onAccountIDClearSelection = () => {
        this.setMultiState({
          accountID: "",
          isAccountIDSelectOpen: false
        });
      };

    private handlePrincipalChange = (value: string): void => {
        this.setMultiState({
            accountId: value,
            isValid: this.checkValid(value, this.state.role)
        })
    };

    private onAccountIDSelect = (_event: any, selection: string | SelectOptionObject, isPlaceholder: boolean | undefined) => {
        if (isPlaceholder) this.onAccountIDClearSelection();
        else {
          this.setMultiState({
            accountId: selection,
            isValid: this.checkValid(selection, this.state.role),
            isAccountIDSelectOpen: false
          });
          console.log('selected:', selection);
        }
      };

    private onAccountIDToggle = (isOpen: boolean) => {
        this.setSingleState("isAccountIDSelectOpen", isOpen);
      };

    private handleRoleChange = (_isChecked: boolean, event: any): void => {
        this.setMultiState({
            role: event.target.value,
            isValid: this.checkValid(this.state.accountId, event.target.value)
        })
    };

    private doGrantAccess = (): void => {
        this.props.onGrant(this.state.accountId as string, 
            this.state.role as string,
            this.props.roles?.find(role => role.principalId == this.state.accountId) !== undefined);
        this.reset();
    }

    private checkValid(accountId: SelectOptionObject | string | undefined, role: string | undefined): boolean {
        if (!accountId) {
            return false;
        }
        if (!role) {
            return false;
        }
        return true;
    }
}
