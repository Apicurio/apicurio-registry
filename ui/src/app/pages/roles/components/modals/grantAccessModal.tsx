/**
 * @license
 * Copyright 2021 Red Hat
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
import {
    Button,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Form,
    FormGroup,
    Modal,
    Popover,
    Radio,
    SelectOptionObject,
    TextInput
} from "@patternfly/react-core";
import { Principal, Services } from "../../../../../services";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { RoleMapping, RoleTypes } from "../../../../../models";
import { OutlinedQuestionCircleIcon } from "@patternfly/react-icons";
import { SelectPrincipalAccount } from "./selectPrincipalAccount";
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
    onGrant: (principal: Principal, role: string, isUpdate: boolean) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface GrantAccessModalState extends PureComponentState {
    isAccountIDSelectOpen: boolean;
    isValid: boolean;
    accountId: string | undefined;
    accountName: string | undefined;
    role: string | undefined;
    escapeClosesModal: boolean;
    isFetchingMappingRole: boolean;
    currentRole: string | undefined;
}


/**
 * Models the modal dialog for granting access to a user.
 */
export class GrantAccessModal extends PureComponent<GrantAccessModalProps, GrantAccessModalState> {

    constructor(props: Readonly<GrantAccessModalProps>) {
        super(props);
    }

    public componentDidMount() {
        this.setMultiState({
            accountId: "",
            accountName: "",
            role: undefined
        });
    }

    public componentDidUpdate(prevProps: GrantAccessModalProps, prevState: GrantAccessModalState) {
        if (this.props.defaultRole !== prevProps.defaultRole) {
            if (this.props.defaultRole) {
                this.setMultiState({
                    accountId: this.props.defaultRole.principalId,
                    accountName: this.props.defaultRole.principalName,
                    currentRole: this.props.defaultRole.role,
                    role: this.props.defaultRole.role
                });
            }
        }

        if (prevState.accountId !== this.state.accountId || prevState.role !== this.state.role) {
            this.setSingleState("isValid", this.checkValid(this.state.accountId, this.state.role));
        }
    }

    public render(): React.ReactElement {
        const { isFetchingMappingRole, accountId } = this.state;
        const { isUpdateAccess, defaultRole } = this.props;
        const principals: Principal[] | (() => Principal[]) | undefined = Services.getConfigService().principals();
        const getPrincipals: () => Principal[] = (typeof principals === "function") ? (principals as () => Principal[]) : () => {
            return principals || [];
        };

        return (
            <Modal
                title="Grant access"
                description={this.modalDescription()}
                variant="medium"
                isOpen={this.props.isOpen}
                onClose={this.closeModal}
                className="grant-access-modal pf-m-redhat-font"
                onEscapePress={this.escapePressed}
                onKeyPress={this.onKey}
                onKeyDown={this.onKey}
                onKeyUp={this.onKey}
                actions={[
                    <Button key="grant" variant="primary" data-testid="modal-btn-grant" onClick={this.doGrantAccess} isDisabled={!this.state.isValid || isFetchingMappingRole}>Save</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.closeModal}>Cancel</Button>
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
                        label="Account"
                        labelIcon={
                            <Popover aria-label="Account help"
                                     headerContent={
                                         <span>Account help</span>
                                     }
                                     bodyContent={
                                         <div>A service account enables your application or tool to connect securely to
                                             your resources. A user account enables users in your organization to access
                                             resources.</div>
                                     }
                            >
                                <OutlinedQuestionCircleIcon/>
                            </Popover>
                        }
                        isRequired
                        fieldId="grant-access-account-id"
                    >
                         {principals ?
                          <SelectPrincipalAccount
                            id={this.state.accountId}
                            onToggle={this.onAccountSelectToggle}
                            onIdUpdate={(id: string) => {
                                this.onAccountIDSelect(getPrincipals, null, id, false);
                            }}
                            initialOptions={getPrincipals}
                            isUpdateAccess={isUpdateAccess}
                            defaultRole={defaultRole}
                            />:
                            <TextInput
                                isRequired
                                type="text"
                                id="grant-access-principal"
                                name="grant-access-principal"
                                aria-describedby="grant-access-principal-helper"
                                onChange={this.handlePrincipalChange}
                                value={accountId}
                                isDisabled={isUpdateAccess}
                            />
                        }
                    </FormGroup>
                    {accountId &&
                    <FormGroup
                        label="Role"
                        isRequired
                        fieldId="grant-access-role"
                    >
                        <Radio id="grant-access-role-admin"
                            className="grant-access-radio-button"
                            name="grant-access-role"
                            label={this.appendString("Administrator",RoleTypes.ADMIN)}
                            description="Assign roles to other accounts on this Service Registry instance, configure global rules, and access data import and export features."
                            value={RoleTypes.ADMIN}
                            onChange={this.handleRoleChange}
                            isChecked={this.state.role == RoleTypes.ADMIN}
                            isDisabled={isFetchingMappingRole}
                        />

                        <Radio id="grant-access-role-manager"
                            className="grant-access-radio-button"
                            name="grant-access-role"
                            label={this.appendString("Manager",RoleTypes.DEVELOPER)}
                            description="Read and write artifacts on this Service Registry instance."
                            value={RoleTypes.DEVELOPER}
                            onChange={this.handleRoleChange}
                            isChecked={this.state.role == RoleTypes.DEVELOPER}
                            isDisabled={isFetchingMappingRole}
                        />

                        <Radio id="grant-access-role-viewer"
                            className="grant-access-radio-button"
                            name="grant-access-role"
                            label={this.appendString("Viewer",RoleTypes.READ_ONLY)}
                            description="Read artifacts on this Service Registry instance."
                            value={RoleTypes.READ_ONLY}
                            onChange={this.handleRoleChange}
                            isChecked={this.state.role == RoleTypes.READ_ONLY}
                            isDisabled={isFetchingMappingRole}
                        />
                    </FormGroup>
                   }
                </Form>

            </Modal>
        );
    }

    protected initializeState(): GrantAccessModalState {
        return {
            accountId: "",
            accountName: "",
            currentRole: undefined,
            escapeClosesModal: true,
            isAccountIDSelectOpen: false,
            isFetchingMappingRole: false,
            isValid: false,
            role: undefined
        };
    }

    private closeModal = () => {
        const {onClose} = this.props;
        if (onClose) {
            onClose();
        }
        this.reset();
    }

    private appendString(roleName: string, roleType: RoleTypes): string {
        const {currentRole} = this.state;
        if (currentRole === roleType) {
            return `${roleName} (current role)`;
        }
        return roleName;
    }

    private reset(): void {
        this.setMultiState(this.initializeState());
    }

    private onAccountIDClearSelection = () => {
        this.setMultiState({
          accountID: "",
          isAccountIDSelectOpen: false,
          role: undefined
        });
      };

    private handlePrincipalChange = (value: string): void => {
        this.setMultiState({
            accountId: value,
            isValid: this.checkValid(value, this.state.role)
        })
    };

    private getRoleMapping = () => {
        const {accountId} = this.state;
        if (accountId) {
            this.setSingleState("isFetchingMappingRole", true);
            Services.getAdminService().getRoleMapping(accountId).then(({role}) => {
                this.setMultiState({
                    role,
                    isFetchingMappingRole: false,
                    currentRole: role
                });
            }).catch((e) => {
                this.setMultiState({
                    role: RoleTypes.READ_ONLY,
                    isFetchingMappingRole: false
                });
            });
        }
    };

    private onAccountIDSelect = (getPrincipals: () => Principal[], _event: any, selection: string | SelectOptionObject, isPlaceholder: boolean | undefined) => {
        if (isPlaceholder) {
            this.onAccountIDClearSelection();
        } else {
            const newState: any = {
                accountId: selection,
                accountName: this.getAccountName(getPrincipals, selection as string),
                isAccountIDSelectOpen: false
            };
            this.setMultiState(newState, () => {
                this.getRoleMapping();
            });
        }
    };


    private handleRoleChange = (isChecked: boolean, event: any): void => {
        this.setMultiState({
            isValid: this.checkValid(this.state.accountId, event.target.value),
            role: event.target.value
        });
    };

    private doGrantAccess = (): void => {
        const principal: Principal = {
            displayName: this.state.accountName,
            id: this.state.accountId as string,
            principalType: "USER_ACCOUNT"
        };
        this.props.onGrant(principal,
            this.state.role as string,
            this.props.roles?.find(role => role.principalId == this.state.accountId) !== undefined);
        this.reset();
    };

    private checkValid(accountId: SelectOptionObject | string | undefined, role: string | undefined): boolean {
        if (!accountId || !role) {
            return false;
        }
        return true;
    }

    private getAccountName(getPrincipals: () => Principal[], accountId: string): string | undefined {
        const principals: Principal[] = getPrincipals();
        if (principals) {
            for (const principal of principals) {
                if (principal.id === accountId) {
                    return principal.displayName;
                }
            }
        }
        return undefined;
    }

    private modalDescription() {
        if (Services.getConfigService().featureMultiTenant()) {
            return "Grant access to resources in this Service Registry instance by assigning a role to an account.";
        } else {
            return "Grant access to resources in the Registry by assigning permissions to an account";
        }
    }

    private escapePressed = (): void => {
        if (this.state.escapeClosesModal) {
            this.closeModal();
        }
    };

    private onAccountSelectToggle = (isOpen: boolean): void => {
        this.setSingleState("escapeClosesModal", !isOpen);
    };

    private onKey = (event: any) => {
        if (event.key === "Enter") {
            event.stopPropagation();
            event.preventDefault();
        }
    };

}
