import React, { FunctionComponent, useState } from "react";
import "./RoleList.css";
import { Button, Modal } from "@patternfly/react-core";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";
import { RoleMappingsEmptyState } from "@app/pages";
import { RoleMapping, RoleTypeObject } from "@sdk/lib/generated-client/models";

/**
 * Properties
 */
export type RoleListProps = {
    roles: RoleMapping[];
    onRevoke: (role: RoleMapping) => void;
    onEdit: (role: RoleMapping) => void;
};


/**
 * Models the list of roles.
 */
export const RoleList: FunctionComponent<RoleListProps> = (props: RoleListProps) => {
    const [isRevokeModalOpen, setIsRevokeModalOpen] = useState(false);
    const [currentRole, setCurrentRole] = useState<RoleMapping>(props.roles[0]);

    const roleName = (role: string): string => {
        switch (role) {
            case RoleTypeObject.DEVELOPER:
                return "Manager";
            case RoleTypeObject.ADMIN:
                return "Administrator";
            case RoleTypeObject.READ_ONLY:
                return "Viewer";
        }
        return role;
    };

    const onRevokeRoleMapping = (role: RoleMapping) => {
        setCurrentRole(role);
        setIsRevokeModalOpen(true);
    };

    const onRevokeModalClose = (): void => {
        setIsRevokeModalOpen(false);
    };

    const doRevokeAccess = (): void => {
        setIsRevokeModalOpen(false);
        props.onRevoke(currentRole);
    };

    const removeRoleConfirmModalBodyText = () => {
        return `${currentRole.principalName || currentRole.principalId} will no longer have access to this Registry instance.`;
    };
    
    const roleActions = (role: RoleMapping) => [
        {
            title: "Edit",
            onClick: () => { props.onEdit(role);}
        },
        {
            title: "Remove",
            onClick: () => { onRevokeRoleMapping(role); }
        }
    ];

    return (
        props.roles.length === 0 ?
            <RoleMappingsEmptyState isFiltered={true}/> :
            <React.Fragment>
                <Table className="role-list">
                    <Thead>
                        <Tr>
                            <Th>Account</Th>
                            <Th>Role</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {props.roles.map((role: RoleMapping, rowIndex: number) =>

                            <Tr key={rowIndex}>
                                <Td>
                                    <div className="principal-id">{ role.principalId }</div>
                                    <div className="principal-name">{ role.principalName }</div>
                                </Td>
                                <Td>{roleName(role.role!)}</Td>
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
                </Table>
                <Modal
                    title="Remove role?"
                    variant="small"
                    isOpen={isRevokeModalOpen}
                    onClose={onRevokeModalClose}
                    className="revoke-access-modal pf-m-redhat-font"
                    actions={[
                        <Button key="revoke" variant="primary" data-testid="modal-btn-revoke" onClick={doRevokeAccess}>Remove</Button>,
                        <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={onRevokeModalClose}>Cancel</Button>
                    ]}
                >
                    <p>{ removeRoleConfirmModalBodyText() }</p>
                </Modal>
            </React.Fragment>
    );

};
