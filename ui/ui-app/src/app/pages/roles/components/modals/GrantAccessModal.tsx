import { FunctionComponent, useEffect, useState } from "react";
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
    TextInput
} from "@patternfly/react-core";
import { Principal } from "@services/config";
import { OutlinedQuestionCircleIcon } from "@patternfly/react-icons";
import "./GrantAccessModal.css";
import { RoleMapping, RoleTypes } from "@models/roleMapping.model.ts";

/**
 * Properties
 */
export type GrantAccessModalProps = {
    isOpen: boolean;
    isUpdateAccess: boolean;
    serviceRegistryInstance?: string;
    accountId?: string;
    roles: null | RoleMapping[];
    defaultRole?: RoleMapping;
    onClose: () => void;
    onGrant: (principal: Principal, role: string, isUpdate: boolean) => void;
};

/**
 * Models the modal dialog for granting access to a user.
 */
export const GrantAccessModal: FunctionComponent<GrantAccessModalProps> = (props: GrantAccessModalProps) => {
    const [accountId, setAccountId] = useState("");
    const [accountName, setAccountName] = useState<string | undefined>("");
    const [currentRole, setCurrentRole] = useState<string | undefined>(undefined);
    const [escapeClosesModal, setEscapeClosesModal] = useState(true);
    const [isFetchingMappingRole, setIsFetchingMappingRole] = useState(false);
    const [isValid, setIsValid] = useState(false);
    const [role, setRole] = useState<string | undefined>(undefined);

    useEffect(() => {
        if (props.isOpen && props.defaultRole) {
            setAccountId(props.defaultRole.principalId);
            setAccountName(props.defaultRole.principalName);
            setCurrentRole(props.defaultRole.role);
            setRole(props.defaultRole.role);
        }
    }, [props]);

    const closeModal = () => {
        const { onClose } = props;
        if (onClose) {
            onClose();
        }
        reset();
    };

    const appendString = (roleName: string, roleType: RoleTypes): string => {
        if (currentRole === roleType) {
            return `${roleName} (current role)`;
        }
        return roleName;
    };

    const reset = (): void => {
        setAccountId("");
        setAccountName("");
        setCurrentRole(undefined);
        setEscapeClosesModal(true);
        setIsFetchingMappingRole(false);
        setIsValid(false);
        setRole(undefined);
    };

    const handlePrincipalChange = (_event: any, value: string): void => {
        setAccountId(value);
        setIsValid(checkValid(value, role));
    };

    const handleRoleChange = (event: any): void => {
        setIsValid(checkValid(accountId, event.target.value));
        setRole(event.target.value);
    };

    const doGrantAccess = (): void => {
        const principal: Principal = {
            displayName: accountName,
            id: accountId as string,
            principalType: "USER_ACCOUNT"
        };
        props.onGrant(principal,
            role as string,
            props.roles?.find(role => role.principalId == accountId) !== undefined);
        reset();
    };

    const checkValid = (accountId: string | undefined, role: string | undefined): boolean => {
        return !(!accountId || !role);
    };

    const modalDescription = (): string => {
        return "Grant access to resources in the Registry by assigning permissions to an account";
    };

    const escapePressed = (): void => {
        if (escapeClosesModal) {
            closeModal();
        }
    };


    const onKey = (event: any) => {
        if (event.key === "Enter") {
            event.stopPropagation();
            event.preventDefault();
        }
    };

    // public componentDidUpdate(prevProps: GrantAccessModalProps, prevState: GrantAccessModalState) {
    //     if (props.defaultRole !== prevProps.defaultRole) {
    //         if (props.defaultRole) {
    //             setMultiState({
    //                 accountId: props.defaultRole.principalId,
    //                 accountName: props.defaultRole.principalName,
    //                 currentRole: props.defaultRole.role,
    //                 role: props.defaultRole.role
    //             });
    //         }
    //     }
    //
    //     if (prevState.accountId !== accountId || prevState.role !== role) {
    //         setSingleState("isValid", checkValid(accountId, role));
    //     }
    // }

    return (
        <Modal
            title="Grant access"
            description={modalDescription()}
            variant="medium"
            isOpen={props.isOpen}
            onClose={closeModal}
            className="grant-access-modal pf-m-redhat-font"
            onEscapePress={escapePressed}
            onKeyPress={onKey}
            onKeyDown={onKey}
            onKeyUp={onKey}
            actions={[
                <Button key="grant" variant="primary" data-testid="modal-btn-grant" onClick={doGrantAccess} isDisabled={!isValid || isFetchingMappingRole}>Save</Button>,
                <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={closeModal}>Cancel</Button>
            ]}
        >
            <Form>
                {props.serviceRegistryInstance !== undefined ? (<DescriptionList>
                    <DescriptionListGroup>
                        <DescriptionListTerm>Registry instance</DescriptionListTerm>
                        <DescriptionListDescription>{props.serviceRegistryInstance}</DescriptionListDescription>
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
                    <TextInput
                        isRequired
                        type="text"
                        id="grant-access-principal"
                        name="grant-access-principal"
                        aria-describedby="grant-access-principal-helper"
                        onChange={handlePrincipalChange}
                        value={accountId}
                        isDisabled={props.isUpdateAccess}
                    />
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
                            label={appendString("Administrator",RoleTypes.ADMIN)}
                            description="Assign roles to other accounts on this Registry instance, configure global rules, and access data import and export features."
                            value={RoleTypes.ADMIN}
                            onChange={handleRoleChange}
                            isChecked={role == RoleTypes.ADMIN}
                            isDisabled={isFetchingMappingRole}
                        />

                        <Radio id="grant-access-role-manager"
                            className="grant-access-radio-button"
                            name="grant-access-role"
                            label={appendString("Manager",RoleTypes.DEVELOPER)}
                            description="Read and write artifacts on this Registry instance."
                            value={RoleTypes.DEVELOPER}
                            onChange={handleRoleChange}
                            isChecked={role == RoleTypes.DEVELOPER}
                            isDisabled={isFetchingMappingRole}
                        />

                        <Radio id="grant-access-role-viewer"
                            className="grant-access-radio-button"
                            name="grant-access-role"
                            label={appendString("Viewer",RoleTypes.READ_ONLY)}
                            description="Read artifacts on this Registry instance."
                            value={RoleTypes.READ_ONLY}
                            onChange={handleRoleChange}
                            isChecked={role == RoleTypes.READ_ONLY}
                            isDisabled={isFetchingMappingRole}
                        />
                    </FormGroup>
                }
            </Form>

        </Modal>
    );

};