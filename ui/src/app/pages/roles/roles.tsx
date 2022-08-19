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
    InputGroup,
    PageSection,
    PageSectionVariants,
    Select,
    SelectOption,
    SelectOptionObject,
    SelectVariant,
    TextInput,
    Toolbar,
    ToolbarContent,
    ToolbarFilter,
    ToolbarGroup,
    ToolbarItem
} from "@patternfly/react-core";
import { SearchIcon } from "@patternfly/react-icons";
import { PageComponent, PageProps, PageState } from "../basePage";
import { RoleMapping } from "../../../models";
import { Principal, Services } from "../../../services";
import { GrantAccessModal, RoleList, RoleMappingsEmptyState } from "./components";
import { PleaseWaitModal, RootPageHeader } from "../../components";
import { If } from "../../components/common/if";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface RolesPageProps extends PageProps {
    principalSelect: any
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
    roleFilterSelectInputValue: string;
    roleFilterSelectInputOpened: boolean;
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
                <PageSection variant={PageSectionVariants.default} isFilled={true} className="ps_role-section">
                    {
                        this.state.roles.length === 0 ?
                            <RoleMappingsEmptyState onCreateRoleMapping={this.onCreateRoleMapping}/>
                            :
                            <>
                                <Toolbar id="toolbar" clearAllFilters={() => {
                                    this.setSingleState("roleFilter", {
                                        principalId: "",
                                        role: "",
                                        principalName: ""
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
                                                    {
                                                        this.isRoleFilterSelected() ?
                                                            <Select
                                                                variant={SelectVariant.single}
                                                                aria-label="Role"
                                                                onToggle={this.onRoleFilterSelectToggle}
                                                                onSelect={this.onRoleFilterSelectChange}
                                                                isOpen={this.state.roleFilterSelectInputOpened}
                                                                placeholderText="Filter by role"
                                                            >
                                                                <SelectOption key={1} value="Administrator" />
                                                                <SelectOption key={2} value="Manager" />
                                                                <SelectOption key={3} value="Viewer" />
                                                            </Select>
                                                            :
                                                            <TextInput value={this.state.roleFilterTextInputValue}
                                                                       placeholder="Filter by account"
                                                                       name="roleFilterInput" id="roleFilterInput"
                                                                       type="search" aria-label="role filter input"
                                                                       onKeyDown={this.onRoleFilterTextInputKeydown}
                                                                       onChange={this.onRoleFilterInputChange} />
                                                    }
                                                    <If condition={this.isAccountFilterSelected}>
                                                        <Button variant={ButtonVariant.control} aria-label="search button for search input" onClick={this.onRoleFilterApplyClick}>
                                                            <SearchIcon />
                                                        </Button>
                                                    </If>
                                                </InputGroup>
                                            </ToolbarItem>
                                            <ToolbarItem>
                                                <Button variant="primary" data-testid="btn-grant-access" onClick={this.onCreateRoleMapping}>Grant access</Button>
                                            </ToolbarItem>
                                            <ToolbarFilter chips={this.state.roleFilter.principalId.length > 0 ? [this.state.roleFilter.principalId] : undefined}
                                                deleteChip={() => {
                                                    this.setSingleState("roleFilter", {
                                                        principalId: "",
                                                        role: this.state.roleFilter.role,
                                                        principalName: ""
                                                    });
                                                }}
                                                categoryName="Account"> </ToolbarFilter>
                                            <ToolbarFilter chips={this.state.roleFilter.role.length > 0 ? [this.state.roleFilter.role] : undefined}
                                                deleteChip={() => {
                                                    this.setSingleState("roleFilter", {
                                                        principalId: this.state.roleFilter.principalId,
                                                        role: "",
                                                        principalName: this.state.roleFilter.principalName,
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
                <PleaseWaitModal message={this.state.pleaseWaitMessage}
                                 isOpen={this.state.isPleaseWaitModalOpen} />
            </React.Fragment>
        );
    }

    protected initializePageState(): RolesPageState {
        return {
            isCreateRoleMappingModalOpen: false,
            isPleaseWaitModalOpen: false,
            isRoleMappingUpdate: false,
            pleaseWaitMessage: "",
            selectedRole: undefined,
            roles: [],
            roleFilter: { principalId: "", role: "", principalName: "" },
            roleListFilterOpened: false,
            roleFilterSelected: roleFilterOptions[0],
            roleFilterTextInputValue: "",
            roleFilterSelectInputValue: "",
            roleFilterSelectInputOpened: false
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
    private onRoleFilterSelectToggle = (isExpanded: boolean): void => {
        this.setSingleState("roleFilterSelectInputOpened", isExpanded)
    }

    private onRoleFilterSelect = (_event: any, selection: string | SelectOptionObject, isPlaceholder: boolean | undefined) => {
        this.setMultiState({
            roleFilterSelected: selection as string,
            roleListFilterOpened: false
        });
    };

    private onRoleFilterInputChange = (value: string) => {
        this.setSingleState("roleFilterTextInputValue", value)
    }
    private onRoleFilterTextInputKeydown = (event: any) => {
        if (event.key === "Enter") {
            this.onRoleFilterApplyClick();
        }
    };
    private onRoleFilterApplyClick = () => {
        let newRoleMappingFilter: RoleMapping = {
            principalId: this.state.roleFilterSelected == roleFilterOptions[0] ? this.state.roleFilterTextInputValue : this.state.roleFilter?.principalId,
            role: this.state.roleFilterSelected == roleFilterOptions[1] ? this.state.roleFilterSelectInputValue : this.state.roleFilter.role,
            principalName: this.state.roleFilterSelected == roleFilterOptions[0] ? this.state.roleFilterTextInputValue : this.state.roleFilter?.principalId
        }
        this.setMultiState({
            roleFilter: newRoleMappingFilter,
            roleFilterTextInputValue: ""
        });
    }
    private onCreateRoleMapping = (): void => {
        this.setSingleState("isCreateRoleMappingModalOpen", true);
    };

    private isAccountFilterSelected = (): boolean => {
        return this.state.roleFilterSelected == roleFilterOptions[0];
    };
    private isRoleFilterSelected = (): boolean => {
        return this.state.roleFilterSelected == roleFilterOptions[1];
    };

    private onRoleFilterSelectChange = (_event: any, selection: string | SelectOptionObject, isPlaceholder: boolean | undefined) => {
        this.setMultiState({
            roleFilterSelectInputValue: selection as string,
            roleFilterSelectInputOpened: false
        }, () => this.onRoleFilterApplyClick());
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

    private updateRoleMapping = (principal: Principal, role: string): void => {
        this.pleaseWait(true, "Granting access, please wait...");
        Services.getAdminService().updateRoleMapping(principal.id, role).then((mapping) => {
            const currentRoleMappings = this.state.roles;
            currentRoleMappings.forEach((role, index) => {
                if (role.principalId === mapping.principalId) {
                    currentRoleMappings[index] = {
                        ...mapping,
                        principalName: principal.displayName as string
                    };
                }
            });

            this.pleaseWait(false, "");
            this.setSingleState("roles", [
                ...currentRoleMappings
            ]);
        }).catch(e => this.handleServerError(e, "Error updating access."));
    };

    private createRoleMapping = (principal: Principal, role: string, isUpdate: boolean): void => {
        this.closeRoleMappingModal();
        if (isUpdate) {
            this.updateRoleMapping(principal, role);
        } else {
            this.pleaseWait(true, "Granting access, please wait...");
            Services.getAdminService().createRoleMapping(principal.id, role, principal.displayName as string).then((mapping) => {
                this.pleaseWait(false, "");
                this.setSingleState("roles", [
                    mapping, ...this.state.roles
                ]);
            }).catch(e => {
                if (e?.error_code === 409) {
                    // If we get a conflict when trying to create, that means the mapping already exists
                    // and we should instead update.
                    this.updateRoleMapping(principal, role);
                } else {
                    this.handleServerError(e, "Error granting access.");
                }
            });
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
