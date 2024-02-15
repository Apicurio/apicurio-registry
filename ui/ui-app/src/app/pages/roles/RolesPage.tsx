import { FunctionComponent, useEffect, useState } from "react";
import "./RolesPage.css";
import { PageSection, PageSectionVariants } from "@patternfly/react-core";
import {
    PageDataLoader,
    PageError,
    PageErrorHandler,
    RoleList,
    RoleMappingsEmptyState,
    RoleToolbar,
    RoleToolbarCriteria,
    toPageError
} from "@app/pages";
import { RootPageHeader } from "@app/components";
import { RoleMapping } from "@models/roleMapping.model.ts";
import { GrantAccessModal } from "@app/pages/roles/components/modals/GrantAccessModal.tsx";
import { If, PleaseWaitModal } from "@apicurio/common-ui-components";
import { AdminService, useAdminService } from "@services/useAdminService.ts";
import { Principal } from "@services/useConfigService.ts";
import { Paging } from "@services/useGroupsService.ts";


export type RolesPageProps = {
    // No props.
}


/**
 * The roles/access page.
 */
// export class RolesPage extends PageComponent<RolesPageProps, RolesPageState> {
export const RolesPage: FunctionComponent<RolesPageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [isGrantAccessOpen, setIsGrantAccessOpen] = useState(false);
    const [isPleaseWaitModalOpen, setIsPleaseWaitModalOpen] = useState(false);
    const [isRoleMappingUpdate, setIsRoleMappingUpdate] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [selectedRole, setSelectedRole] = useState<RoleMapping>();
    const [roles, setRoles] = useState<RoleMapping[]>([]);
    const [criteria, setCriteria] = useState<RoleToolbarCriteria>();

    const admin: AdminService = useAdminService();

    const createLoaders = (): Promise<any> => {
        const paging: Paging = {
            page: 1,
            pageSize: 100
        };
        return admin.getRoleMappings(paging).then(results => setRoles(results.roleMappings))
            .catch(error => {
                setPageError(toPageError(error, "Error loading role mappings."));
            });
    };

    const onCriteriaChange = (criteria: RoleToolbarCriteria): void => {
        setCriteria(criteria);
    };

    const onCreateRoleMapping = (): void => {
        setIsGrantAccessOpen(true);
    };

    const closeRoleMappingModal = (): void => {
        setSelectedRole(undefined);
        setIsRoleMappingUpdate(false);
        setIsGrantAccessOpen(false);
    };

    const onEditRoleMapping = (role: RoleMapping) => {
        console.debug(role);
        setSelectedRole(role);
        setIsRoleMappingUpdate(true);
        onCreateRoleMapping();
    };

    const updateRoleMapping = (principal: Principal, role: string): void => {
        pleaseWait(true, "Granting access, please wait...");
        admin.updateRoleMapping(principal.id, role).then((mapping) => {
            const currentRoleMappings = roles;
            currentRoleMappings.forEach((role, index) => {
                if (role.principalId === mapping.principalId) {
                    currentRoleMappings[index] = {
                        ...mapping,
                        principalName: principal.displayName as string
                    };
                }
            });

            pleaseWait(false, "");
            setRoles([...currentRoleMappings]);
        }).catch(error => {
            setPageError(toPageError(error, "Error updating access."));
        });
    };

    const createRoleMapping = (principal: Principal, role: string, isUpdate: boolean): void => {
        closeRoleMappingModal();
        if (isUpdate) {
            updateRoleMapping(principal, role);
        } else {
            pleaseWait(true, "Granting access, please wait...");
            admin.createRoleMapping(principal.id, role, principal.displayName as string).then((mapping) => {
                pleaseWait(false, "");
                setRoles([mapping, ...roles]);
            }).catch(error => {
                if (error?.error_code === 409) {
                    // If we get a conflict when trying to create, that means the mapping already exists
                    // and we should instead update.
                    updateRoleMapping(principal, role);
                } else {
                    setPageError(toPageError(error, "Error granting access."));
                }
            });
        }
    };

    const onRevokeRoleMapping = (role: RoleMapping): void => {
        pleaseWait(true, `Revoking access for ${role.principalName || role.principalId}, please wait...`);
        admin.deleteRoleMapping(role.principalId).then(() => {
            pleaseWait(false, "");
            removeMapping(role.principalId);
        }).catch(error => {
            setPageError(toPageError(error, "Error revoking access."));
        });
    };

    const pleaseWait = (isOpen: boolean, message: string): void => {
        setIsPleaseWaitModalOpen(isOpen);
        setPleaseWaitMessage(message);
    };

    const removeMapping = (principalId: string): void => {
        const newRoles: RoleMapping[] =
        roles.filter(rm => {
            return rm.principalId !== principalId;
        });
        setRoles(newRoles);
    };

    const acceptRole = (role: RoleMapping): boolean => {
        if (criteria?.filterType === "account" && criteria?.filterValue) {
            const idAndName: string = `${role.principalId}(${role.principalName || ""})`;
            return idAndName.toLowerCase().indexOf(criteria.filterValue.toLowerCase()) >= 0;
        } else if (criteria?.filterType === "role" && criteria?.filterValue !== "") {
            return criteria.filterValue === role.role;
        } else {
            return true;
        }
    };

    const filterRoles = (): RoleMapping[] => {
        // Filter the roles by the criteria.
        const filteredRoles: RoleMapping[] = roles.filter(acceptRole);

        // Then sort the filtered results.
        filteredRoles.sort((a, b) => {
            const ascending: boolean = criteria ? criteria.ascending : true;
            const direction: number = ascending ? 1 : -1;
            return a.principalId.localeCompare(b.principalId) * direction;
        });

        // Now handle pagination
        const fromIndex: number = ((criteria?.paging.page || 1) - 1) * (criteria?.paging.pageSize || 10);
        const toIndex: number = fromIndex + (criteria?.paging.pageSize || 10);
        return filteredRoles.slice(fromIndex, toIndex);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    const filteredRoles: RoleMapping[] = filterRoles();

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection className="ps_roles-header" variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={2} />
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true} className="ps_role-section">
                    <If condition={roles.length === 0}>
                        <RoleMappingsEmptyState onCreateRoleMapping={onCreateRoleMapping}/>
                    </If>
                    <If condition={roles.length > 0}>
                        <RoleToolbar roles={filteredRoles} onCriteriaChange={onCriteriaChange} onGrantAccess={onCreateRoleMapping} />
                        <RoleList roles={filteredRoles} onRevoke={onRevokeRoleMapping}
                            onEdit={onEditRoleMapping}></RoleList>
                    </If>
                </PageSection>
            </PageDataLoader>
            <GrantAccessModal isOpen={isGrantAccessOpen}
                isUpdateAccess={isRoleMappingUpdate}
                onClose={closeRoleMappingModal}
                onGrant={createRoleMapping}
                roles={isRoleMappingUpdate ? roles : null}
                defaultRole={selectedRole} />
            <PleaseWaitModal message={pleaseWaitMessage} isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
