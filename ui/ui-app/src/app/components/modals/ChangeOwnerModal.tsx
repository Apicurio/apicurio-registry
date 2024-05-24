import { FunctionComponent, useEffect, useState } from "react";
import { Button, Form, FormGroup, Modal, Text, TextInput } from "@patternfly/react-core";


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

    // Validate the inputs.
    useEffect(() => {
        if (newOwner && newOwner.trim().length > 0 && newOwner !== currentOwner) {
            setValid(true);
        } else {
            setValid(false);
        }
    }, [isOpen, currentOwner, newOwner]);

    const onNewOwner = (_event: any, value: string): void => {
        setNewOwner(value);
    };

    return (
        <Modal
            title="Change owner"
            variant="medium"
            isOpen={isOpen}
            onClose={onClose}
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
                    <TextInput
                        isRequired={true}
                        type="text"
                        id="form-new-owner"
                        data-testid="form-new-owner"
                        name="form-new-owner"
                        aria-describedby="form-new-owner-helper"
                        value={newOwner}
                        onChange={onNewOwner}
                    />
                </FormGroup>
            </Form>
        </Modal>
    );

};
