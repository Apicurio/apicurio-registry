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
import {
    Button,
    Modal
} from '@patternfly/react-core';
import { TableComposable, Thead, Tbody, Tr, Th, Td } from '@patternfly/react-table';
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { RoleMapping, RoleTypes } from "../../../../../models";
import { RoleMappingsEmptyState } from '../empty';

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
                title: 'Revoke Access',
                onClick: () => {this.onRevokeRoleMapping(role.principalId)}
            }
        ];

        let filteredRoles = this.props.roles.sort((rm1, rm2) => {
            return rm1.principalId.localeCompare(rm2.principalId);
        }).filter((role: RoleMapping)=>{
            if(this.props.roleFilter.principalId.length > 0) {
                return role.principalId.includes(this.props.roleFilter.principalId);
            } 
            return true;
        }).filter((role: RoleMapping)=>{
            if (this.props.roleFilter.role.length > 0) {
                switch (role.role) {
                    case RoleTypes.DEVELOPER:
                        return "Manager".includes(this.props.roleFilter.role);
                    case RoleTypes.ADMIN:
                        return "Admin".includes(this.props.roleFilter.role);
                    case RoleTypes.READ_ONLY:
                        return "Viewer".includes(this.props.roleFilter.role);
                }
            }
            return true;
        });
        return (
            filteredRoles.length === 0 ?
                <RoleMappingsEmptyState isFiltered={true}/> :
            <React.Fragment>
                <TableComposable>
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
                                <Td>{role.principalId}</Td>
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
                    title="Revoke Access"
                    variant="small"
                    isOpen={this.state.isRevokeModalOpen}
                    onClose={this.onRevokeModalClose}
                    className="revoke-access-modal pf-m-redhat-font"
                    actions={[
                        <Button key="revoke" variant="primary" data-testid="modal-btn-revoke" onClick={this.doRevokeAccess}>Revoke</Button>,
                        <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.onRevokeModalClose}>Cancel</Button>
                    ]}
                >
                    <p>Do you really want to revoke {this.state.revokingPrincipalId}'s access?</p>
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
                return "Admin";
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

}
