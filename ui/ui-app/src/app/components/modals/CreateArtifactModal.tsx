import { FunctionComponent, useEffect, useState } from "react";
import { Button, Modal } from "@patternfly/react-core";
import { CreateArtifactForm } from "@app/components";
import { CreateArtifact } from "@sdk/lib/generated-client/models";

const EMPTY_FORM_DATA: CreateArtifact = {
};

/**
 * Properties
 */
export type CreateArtifactModalProps = {
    groupId?: string;
    isOpen: boolean;
    onClose: () => void;
    onCreate: (groupId: string|null, data: CreateArtifact) => void;
};

/**
 * Models the Create Artifact modal dialog.
 */
export const CreateArtifactModal: FunctionComponent<CreateArtifactModalProps> = (props: CreateArtifactModalProps) => {
    const [isFormValid, setFormValid] = useState<boolean>(false);
    const [groupId, setGroupId] = useState<string|null>(null);
    const [formData, setFormData] = useState<CreateArtifact>(EMPTY_FORM_DATA);

    const onCreateArtifactFormValid = (isValid: boolean): void => {
        setFormValid(isValid);
    };

    const onCreateArtifactFormChange = (groupId: string|null, data: CreateArtifact): void => {
        setGroupId(groupId);
        setFormData(data);
    };

    const fireCloseEvent = (): void => {
        props.onClose();
    };

    const fireCreateEvent = (): void => {
        props.onCreate(groupId, formData);
    };

    useEffect(() => {
        if (props.isOpen) {
            setFormValid(false);
            setFormData(EMPTY_FORM_DATA);
        }
    }, [props.isOpen]);

    return (
        <Modal
            title="Create Artifact"
            variant="large"
            isOpen={props.isOpen}
            onClose={fireCloseEvent}
            className="create-artifact-modal pf-m-redhat-font"
            actions={[
                <Button key="create" variant="primary" data-testid="create-artifact-modal-btn-create" onClick={fireCreateEvent} isDisabled={!isFormValid}>Create</Button>,
                <Button key="cancel" variant="link" data-testid="create-artifact-modal-btn-cancel" onClick={fireCloseEvent}>Cancel</Button>
            ]}
        >
            <CreateArtifactForm onChange={onCreateArtifactFormChange} onValid={onCreateArtifactFormValid} groupId={props.groupId} />
        </Modal>
    );

};
