import React, { FunctionComponent, useEffect, useState } from "react";
import "./editMetaDataModal.css";
import { Button, Form, FormGroup, Modal, Text, TextInput } from "@patternfly/react-core";
import { Principal, Services } from "../../../../../services";
import { SelectPrincipalAccount } from "../../../roles";


/**
 * Properties
 */
export type ChangeOwnerModalProps = {
    isOpen: boolean;
    currentOwner: string;
    onClose: () => void;
    onChangeOwner: (newOwner: string) => void;
};

export const ChangeOwnerModal: FunctionComponent<ChangeOwnerModalProps> = (
    { isOpen, onClose, currentOwner, onChangeOwner }: ChangeOwnerModalProps) => {

    const [isValid, setValid] = useState(false);
    const [newOwner, setNewOwner] = useState<string>();
    const [isAccountToggled, setAccountToggled] = useState(false);
    const principals: Principal[] | (() => Principal[]) | undefined = Services.getConfigService().principals();
    const getPrincipals: () => Principal[] = (typeof principals === "function") ? (principals as () => Principal[]) : () => {
        return principals || [];
    };

    // Validate the inputs.
    useEffect(() => {
        if (newOwner && newOwner.trim().length > 0 && newOwner !== currentOwner) {
            setValid(true);
        } else {
            setValid(false);
        }
    }, [isOpen, newOwner]);

    const onEscapePressed = () => {
        if (!isAccountToggled) {
            onClose();
        }
    };

    return (
        <Modal
            title="Change owner"
            variant="medium"
            isOpen={isOpen}
            onClose={onClose}
            onEscapePress={onEscapePressed}
            className="change-owner pf-m-redhat-font"
            actions={[
                <Button key="edit" variant="primary" data-testid="modal-btn-edit" onClick={() => { onChangeOwner(newOwner || ""); }} isDisabled={!isValid}>Change owner</Button>,
                <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={onClose}>Cancel</Button>
            ]}
        >
            <Form>
                <FormGroup label="Current owner" fieldId="form-current-owner">
                    <Text>{ currentOwner }</Text>
                </FormGroup>
                <FormGroup label="New owner" fieldId="form-new-owner" isRequired={true}>
                    {
                        principals ?
                            <SelectPrincipalAccount
                                id={newOwner}
                                onToggle={(isToggled) => {
                                    setAccountToggled(isToggled);
                                }}
                                onIdUpdate={(id: string) => {
                                    console.debug("=====> ID update: ", id);
                                    setNewOwner(id);
                                }}
                                isUsersOnly={false}
                                initialOptions={getPrincipals}
                                isUpdateAccess={false}
                            />:
                            <TextInput
                                isRequired={true}
                                type="text"
                                id="form-new-owner"
                                data-testid="form-new-owner"
                                name="form-new-owner"
                                aria-describedby="form-new-owner-helper"
                                value={newOwner}
                                onChange={setNewOwner}
                            />
                    }
                </FormGroup>
            </Form>
        </Modal>
    );

};
