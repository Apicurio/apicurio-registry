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
import "./roles.css";
import {
    Button,
    Flex,
    FlexItem,
    Modal,
    PageSection,
    PageSectionVariants,
    Spinner,
    TextContent
} from '@patternfly/react-core';
import {PageComponent, PageProps, PageState} from "../basePage";
import {RolesPageHeader} from "./components/pageheader";
import {RoleMapping} from "../../../models";
import {Services} from "../../../services";
import {GrantAccessModal, RoleList, RoleMappingsEmptyState} from "./components";
import {PleaseWaitModal} from "../../components";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface RolesPageProps extends PageProps {

}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface RolesPageState extends PageState {
    isCreateRoleMappingModalOpen: boolean;
    roles: RoleMapping[];
    isPleaseWaitModalOpen: boolean;
    pleaseWaitMessage: string;
}

/**
 * The global roles page.
 */
export class RolesPage extends PageComponent<RolesPageProps, RolesPageState> {

    constructor(props: Readonly<RolesPageProps>) {
        super(props);
    }

    public renderPage(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_roles-header" variant={PageSectionVariants.light}>
                    <RolesPageHeader />
                </PageSection>
                <PageSection className="ps_roles-description" variant={PageSectionVariants.light}>
                    <Flex>
                        <FlexItem>
                            <TextContent>
                                Manage access to the registry by granting/revoking roles to specific users.
                            </TextContent>
                        </FlexItem>
                        <FlexItem align={{default : "alignRight"}}>
                            <Button variant="primary" data-testid="btn-grant-access" onClick={this.onCreateRoleMapping}>Grant Access</Button>
                        </FlexItem>
                    </Flex>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    {
                        this.isLoading() ?
                            <Flex>
                                <FlexItem><Spinner size="lg"/></FlexItem>
                                <FlexItem><span>Loading, please wait...</span></FlexItem>
                            </Flex>
                            : this.state.roles.length === 0 ?
                            <RoleMappingsEmptyState />
                            :
                            <RoleList roles={this.state.roles} onRevoke={this.onRevokeRoleMapping}></RoleList>
                    }
                </PageSection>
                <GrantAccessModal isOpen={this.state.isCreateRoleMappingModalOpen}
                                  onClose={this.closeRoleMappingModal}
                                  onGrant={this.createRoleMapping} />
                <PleaseWaitModal message={this.state.pleaseWaitMessage}
                                 isOpen={this.state.isPleaseWaitModalOpen} />
            </React.Fragment>
        );
    }

    protected initializePageState(): RolesPageState {
        return {
            isCreateRoleMappingModalOpen: false,
            isPleaseWaitModalOpen: false,
            pleaseWaitMessage: "",
            isLoading: true,
            roles: []
        };
    }

    // @ts-ignore
    protected createLoaders(): Promise {
        return Services.getAdminService().getRoleMappings().then( roles => {
                this.setMultiState({
                    isLoading: false,
                    roles
                });
            });
    }

    private onCreateRoleMapping = (): void => {
        this.setSingleState("isCreateRoleMappingModalOpen", true);
    };

    private closeRoleMappingModal = (): void => {
        this.setSingleState("isCreateRoleMappingModalOpen", false);
    };

    private createRoleMapping = (principalId: string, role: string): void => {
        this.closeRoleMappingModal();
        this.pleaseWait(true, "Granting access, please wait...");
        Services.getAdminService().createRoleMapping(principalId, role).then( (mapping) => {
            this.pleaseWait(false, "");
            this.setSingleState("roles", [
                mapping, ...this.state.roles
            ]);
        }).catch(e => this.handleServerError(e, "Error granting access."));
    };

    private onRevokeRoleMapping = (principalId: string): void => {
        this.pleaseWait(true, `Revoking access for ${principalId}, please wait...`);
        Services.getAdminService().deleteRoleMapping(principalId).then(() => {
            this.pleaseWait(false, "");
            this.removeMapping(principalId);
        }).catch(e => this.handleServerError(e, "Error revoking access."));
    };

    private pleaseWait = (isOpen: boolean, message: string): void => {
        this.setMultiState({
            isPleaseWaitModalOpen: isOpen,
            pleaseWaitMessage: message
        });
    };

    private removeMapping(principalId: string): void {
        const newRoles: RoleMapping[] =
            this.state.roles.filter(rm => {
                return rm.principalId !== principalId;
            });
        this.setSingleState("roles", newRoles);
    }

}
