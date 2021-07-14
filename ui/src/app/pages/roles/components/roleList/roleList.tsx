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
    DataList,
    DataListAction,
    DataListCell,
    DataListItemCells,
    DataListItemRow, Modal
} from '@patternfly/react-core';
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {RoleMapping, RoleTypes} from "../../../../../models";
import {UserIcon, UnlockIcon} from "@patternfly/react-icons";

/**
 * Properties
 */
export interface RoleListProps extends PureComponentProps {
    roles: RoleMapping[];
    onRevoke: (principalId: string) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface RoleListState extends PureComponentState {
    isRevokeModalOpen: boolean;
    revokingPrincipalId: string;
}


/**
 * Models the list of roles.
 */
export class RoleList extends PureComponent<RoleListProps, RoleListState> {

    constructor(props: Readonly<RoleListProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <DataList aria-label="List of roles" className="role-list">
                    {
                        this.props.roles.sort((rm1, rm2) => {
                            return rm1.principalId.localeCompare(rm2.principalId);
                        }).map( (role, idx) =>
                                <DataListItemRow className="role-list-item" key={role.principalId}>
                                    <DataListItemCells
                                        dataListCells={[
                                            <DataListCell key="type icon" className="type-icon-cell">
                                                <UserIcon />
                                            </DataListCell>,
                                            <DataListCell key="main content" className="content-cell">
                                                <span className="role-txt role-principal">{ role.principalId }</span>
                                                <span className="role-txt">has been granted</span>
                                                <span className="role-txt role-role">{ this.roleName(role.role) }</span>
                                                <span className="role-txt">access.</span>
                                            </DataListCell>
                                        ]}
                                    />
                                    <DataListAction
                                        aria-labelledby={`role-list-revoke-action-${idx}`}
                                        id={`role-list-revoke-action-${idx}`}
                                        aria-label="Revoke Access"
                                    >
                                        <Button key={role.principalId} variant="secondary" className="role-revoke-action"
                                                onClick={this.onRevokeRoleMapping(role.principalId)}>
                                            <UnlockIcon className="role-revoke-icon" />
                                            <span>Revoke</span>
                                        </Button>
                                    </DataListAction>
                                </DataListItemRow>
                        )
                    }
                </DataList>
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
                    <p>Do you really want to revoke { this.state.revokingPrincipalId }'s access?</p>
                </Modal>
            </React.Fragment>
        );
    }

    protected initializeState(): RoleListState {
        return {
            isRevokeModalOpen: false,
            revokingPrincipalId: ""
        };
    }

    private roleName(role: string): string {
        switch (role) {
            case RoleTypes.DEVELOPER:
                return "Developer";
            case RoleTypes.ADMIN:
                return "Admin";
            case RoleTypes.READ_ONLY:
                return "Viewer";
        }
        return role;
    }

    private onRevokeRoleMapping(principalId: string): () => void {
        return () => {
            this.setMultiState({
                isRevokeModalOpen: true,
                revokingPrincipalId: principalId
            });
        };
    };

    private onRevokeModalClose = (): void => {
        this.setSingleState("isRevokeModalOpen", false);
    };

    private doRevokeAccess = (): void => {
        this.onRevokeModalClose();
        this.props.onRevoke(this.state.revokingPrincipalId);
    }

}
