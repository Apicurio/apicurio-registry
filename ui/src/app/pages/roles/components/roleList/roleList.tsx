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
import "./roleList.css";
import { Button, Modal } from "@patternfly/react-core";
import { TableComposable, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { RoleMapping, RoleTypes } from "../../../../../models";
import { RoleMappingsEmptyState } from "../empty";
import { Services } from "../../../../../services";

/**
 * Properties
 */
export interface RoleListProps extends PureComponentProps {
    roles: RoleMapping[];
    roleFilter: RoleMapping;
    onRevoke: (principalId: string) => void;
    onEditRoleMapping: (role: RoleMapping) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface RoleListState extends PureComponentState {
    isRevokeModalOpen: boolean;
    revokingPrincipalId: string;
    currentRole: RoleMapping;
}

/**
 * Models the list of roles.
 */
export class RoleList extends PureComponent<RoleListProps, RoleListState> {

    constructor(props: Readonly<RoleListProps>) {
        super(props);
    }

    public render(): React.ReactElement {

        const roleActions = (role: RoleMapping) => [
            {
                title: 'Edit',
                onClick: () => { this.props.onEditRoleMapping(role)}
            },
            {
                title: 'Remove',
                onClick: () => {this.onRevokeRoleMapping(role.principalId)}
            }
        ];

        let filteredRoles = this.props.roles.sort((rm1, rm2) => {
            return rm1.principalId.localeCompare(rm2.principalId);
        }).filter((role: RoleMapping)=>{
            let match: boolean = false;
            let mustMatch: boolean = false;
            if(this.props.roleFilter.principalId.length > 0) {
                mustMatch = true;
                match = match || role.principalId.toLowerCase().includes(this.props.roleFilter.principalId.toLowerCase());
            }
            if(this.props.roleFilter.principalName.length > 0) {
                mustMatch = true;
                match = match || role.principalName.toLowerCase().includes(this.props.roleFilter.principalName.toLowerCase());
            }
            return mustMatch ? match : true;
        }).filter((role: RoleMapping)=>{
            if (this.props.roleFilter.role.length > 0) {
                switch (role.role) {
                    case RoleTypes.DEVELOPER:
                        return "Manager" == this.props.roleFilter.role;
                    case RoleTypes.ADMIN:
                        return "Administrator" == this.props.roleFilter.role;
                    case RoleTypes.READ_ONLY:
                        return "Viewer" == this.props.roleFilter.role;
                }
            }
            return true;
        });
        return (
            filteredRoles.length === 0 ?
                <RoleMappingsEmptyState isFiltered={true}/> :
            <React.Fragment>
                <TableComposable className="role-list">
                    <Thead>
                        <Tr>
                            {/* <Th
                            /> */}
                            <Th>Account</Th>
                            <Th>Role</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {filteredRoles.map((role, rowIndex) =>

                            <Tr key={rowIndex}>
                                {/* Disable for now until we want to support multi-select.
                                <Td
                                    key={`${rowIndex}_0`}
                                    select={{
                                        rowIndex,
                                        onSelect: this.onSelect,
                                        isSelected: false,
                                    }}
                                /> */}
                                <Td>
                                    <div className="principal-id">{ role.principalId }</div>
                                    <div className="principal-name">{ role.principalName }</div>
                                </Td>
                                <Td>{this.roleName(role.role)}</Td>
                                <Td className = "role-list-action-column"
                                    key={`${rowIndex}_2`}
                                    actions={{
                                        items: roleActions(role)
                                    }}
                                />
                            </Tr>
                        )
                        }
                    </Tbody>
                </TableComposable>
                <Modal
                    title="Remove role?"
                    variant="small"
                    isOpen={this.state.isRevokeModalOpen}
                    onClose={this.onRevokeModalClose}
                    className="revoke-access-modal pf-m-redhat-font"
                    actions={[
                        <Button key="revoke" variant="primary" data-testid="modal-btn-revoke" onClick={this.doRevokeAccess}>Remove</Button>,
                        <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.onRevokeModalClose}>Cancel</Button>
                    ]}
                >
                    <p>{ this.removeRoleConfirmModalBodyText() }</p>
                </Modal>
            </React.Fragment>
        );
    }

    protected initializeState(): RoleListState {
        return {
            isRevokeModalOpen: false,
            revokingPrincipalId: "",
            currentRole: this.props.roles[0],
        };
    }

    private roleName(role: string): string {
        switch (role) {
            case RoleTypes.DEVELOPER:
                return "Manager";
            case RoleTypes.ADMIN:
                return "Administrator";
            case RoleTypes.READ_ONLY:
                return "Viewer";
        }
        return role;
    }

    private onRevokeRoleMapping = (principalId: string) => {
        this.setMultiState({
            isRevokeModalOpen: true,
            revokingPrincipalId: principalId
        });
    };

    private onRevokeModalClose = (): void => {
        this.setSingleState("isRevokeModalOpen", false);
    };

    private doRevokeAccess = (): void => {
        this.onRevokeModalClose();
        this.props.onRevoke(this.state.revokingPrincipalId);
    }

    private removeRoleConfirmModalBodyText() {
        if (Services.getConfigService().featureMultiTenant()) {
            return `Do you really want to revoke ${this.state.revokingPrincipalId}'s access?`;
        } else {
            return `${this.state.revokingPrincipalId} will no longer have access to this Service Registry instance.`;
        }
    }
}
