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
    ButtonVariant,
    Flex,
    FlexItem,
    InputGroup,
    PageSection,
    PageSectionVariants,
    Select,
    SelectOption,
    SelectOptionObject,
    SelectVariant,
    TextContent,
    TextInput,
    Toolbar,
    ToolbarContent,
    ToolbarFilter,
    ToolbarGroup,
    ToolbarItem
} from '@patternfly/react-core';
import { SearchIcon } from "@patternfly/react-icons";
import {PageComponent, PageProps, PageState} from "../basePage";
import {RoleMapping} from "../../../models";
import {Services} from "../../../services";
import {GrantAccessModal, RoleList, RoleMappingsEmptyState} from "./components";
import {PleaseWaitModal, RootPageHeader} from "../../components";


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
    isRoleMappingUpdate: boolean;
    roles: RoleMapping[];
    roleFilter: RoleMapping;
    roleListFilterOpened: boolean;
    roleFilterSelected: string;
    roleFilterTextInputValue: string;
    selectedRole: RoleMapping | undefined;
    isPleaseWaitModalOpen: boolean;
    pleaseWaitMessage: string;
}

const roleFilterOptions = ['Account', 'Role'];
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
                <PageSection className="ps_roles-header" variant={PageSectionVariants.light} padding={{ default : "noPadding" }}>
                    <RootPageHeader tabKey={2} />
                </PageSection>
                <PageSection className="ps_roles-description" variant={PageSectionVariants.light}>
                    <Flex>
                        <FlexItem>
                            <TextContent>
                                Manage access to the registry by granting/revoking roles to specific users.
                            </TextContent>
                        </FlexItem>
                    </Flex>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true} className="ps_role-section">
                    {
                        this.state.roles.length === 0 ?
                            <RoleMappingsEmptyState onCreateRoleMapping={this.onCreateRoleMapping}/>
                            :
                            <>
                                <Toolbar id="toolbar" clearAllFilters={() => {
                                    this.setSingleState("roleFilter", {
                                        principalId: "",
                                        role: ""
                                    });
                                }}>
                                    <ToolbarContent>
                                        <ToolbarGroup>
                                            <ToolbarItem className="ps_role-filter-select-toolbar-item">
                                                <Select
                                                    variant={SelectVariant.single}
                                                    aria-label="Filter On"
                                                    onToggle={this.onRoleFilterToggle}
                                                    onSelect={this.onRoleFilterSelect}
                                                    selections={this.state.roleFilterSelected}
                                                    isOpen={this.state.roleListFilterOpened}
                                                >
                                                    {roleFilterOptions.map((option, index) => (
                                                        <SelectOption key={index} value={option} />
                                                    ))}
                                                </Select>
                                            </ToolbarItem>
                                            <ToolbarItem>
                                                <InputGroup>
                                                    <TextInput value={this.state.roleFilterTextInputValue} name="roleFilterInput" id="roleFilterInput" type="search" aria-label="role filter input" onChange={this.onRoleFilterInputChange} />
                                                    <Button variant={ButtonVariant.control} aria-label="search button for search input" onClick={this.onRoleFilterApplyClick}>
                                                        <SearchIcon />
                                                    </Button>
                                                </InputGroup>
                                            </ToolbarItem>
                                            <ToolbarItem>
                                                <Button variant="primary" data-testid="btn-grant-access" onClick={this.onCreateRoleMapping}>Grant Access</Button>
                                            </ToolbarItem>
                                            <ToolbarFilter chips={this.state.roleFilter.principalId.length > 0 ? [this.state.roleFilter.principalId] : undefined}
                                                deleteChip={() => {
                                                    this.setSingleState("roleFilter", {
                                                        principalId: "",
                                                        role: this.state.roleFilter.role
                                                    });
                                                }}
                                                categoryName="Account"> </ToolbarFilter>
                                            <ToolbarFilter chips={this.state.roleFilter.role.length > 0 ? [this.state.roleFilter.role] : undefined}
                                                deleteChip={() => {
                                                    this.setSingleState("roleFilter", {
                                                        principalId: this.state.roleFilter.principalId,
                                                        role: ""
                                                    });
                                                }}
                                                categoryName="Role"> </ToolbarFilter>

                                        </ToolbarGroup>
                                    </ToolbarContent>
                                </Toolbar>
                                <RoleList roles={this.state.roles} roleFilter={this.state.roleFilter} onRevoke={this.onRevokeRoleMapping} onEditRoleMapping={this.onEditRoleMapping}></RoleList>
                            </>}
                </PageSection>
                <GrantAccessModal isOpen={this.state.isCreateRoleMappingModalOpen}
                    isUpdateAccess={this.state.isRoleMappingUpdate}
                    onClose={this.closeRoleMappingModal}
                    onGrant={this.createRoleMapping}
                    roles={this.state.isRoleMappingUpdate ? this.state.roles : null}
                    defaultRole={this.state.selectedRole} />
                {this.state.isPleaseWaitModalOpen ? <PleaseWaitModal message={this.state.pleaseWaitMessage}
                    isOpen={this.state.isPleaseWaitModalOpen} /> : <></>}
            </React.Fragment>
        );
    }

    protected initializePageState(): RolesPageState {
        return {
            isCreateRoleMappingModalOpen: false,
            isPleaseWaitModalOpen: false,
            isRoleMappingUpdate: false,
            pleaseWaitMessage: "",
            isLoading: true,
            selectedRole: undefined,
            roles: [],
            roleFilter: { principalId: "", role: "" },
            roleListFilterOpened: false,
            roleFilterSelected: roleFilterOptions[0],
            roleFilterTextInputValue: ""
        };
    }

    // @ts-ignore
    protected createLoaders(): Promise {
        return Services.getAdminService().getRoleMappings().then(roles => {
            this.setMultiState({
                isLoading: false,
                roles
            });
        });
    }

    private onRoleFilterToggle = (isExpanded: boolean): void => {
        this.setSingleState("roleListFilterOpened", isExpanded)
    }

    private onRoleFilterSelect = (_event: any, selection: string | SelectOptionObject, isPlaceholder: boolean | undefined) => {
        this.setMultiState({
            roleFilterSelected: selection,
            roleListFilterOpened: false
        });
    };

    private onRoleFilterInputChange = (value: string) => {
        this.setSingleState("roleFilterTextInputValue", value)
    }
    private onRoleFilterApplyClick = () => {
        let newRoleMappingFilter: RoleMapping = {
            principalId: this.state.roleFilterSelected == roleFilterOptions[0] ? this.state.roleFilterTextInputValue : this.state.roleFilter?.principalId,
            role: this.state.roleFilterSelected == roleFilterOptions[1] ? this.state.roleFilterTextInputValue : this.state.roleFilter.role
        }
        this.setSingleState("roleFilter", newRoleMappingFilter);
    }
    private onCreateRoleMapping = (): void => {
        this.setSingleState("isCreateRoleMappingModalOpen", true);
    };

    private closeRoleMappingModal = (): void => {
        this.setMultiState({
            selectedRole: undefined,
            isRoleMappingUpdate: false,
            isCreateRoleMappingModalOpen: false
        });
    };

    private onEditRoleMapping = (role: RoleMapping) => {
        this.setMultiState({
            selectedRole: role,
            isRoleMappingUpdate: true
        })
        this.onCreateRoleMapping();
    }

    private onUpdateRoleMapping = (principalId: string, role: string): void => {
        this.pleaseWait(true, "Granting access, please wait...");
        Services.getAdminService().updateRoleMapping(principalId, role).then((mapping) => {
            let currentRoleMappings = this.state.roles;
            currentRoleMappings.map((role, index) => {
                if (role.principalId == mapping.principalId) {
                    currentRoleMappings[index] = mapping;
                    console.log("found role")
                }
            })

            this.pleaseWait(false, "");
            this.setSingleState("roles", [
                ...currentRoleMappings
            ]);
        }).catch(e => this.handleServerError(e, "Error updating access."));
    };

    private createRoleMapping = (principalId: string, role: string, isUpdate: boolean): void => {
        this.closeRoleMappingModal();
        if (isUpdate) {
            this.onUpdateRoleMapping(principalId, role);
        } else {
            this.pleaseWait(true, "Granting access, please wait...");
            Services.getAdminService().createRoleMapping(principalId, role).then((mapping) => {
                this.pleaseWait(false, "");
                this.setSingleState("roles", [
                    mapping, ...this.state.roles
                ]);
            }).catch(e => this.handleServerError(e, "Error granting access."));
        }
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
