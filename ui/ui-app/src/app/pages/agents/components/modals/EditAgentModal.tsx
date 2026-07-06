import { FunctionComponent, useEffect, useState } from "react";
import "./EditAgentModal.css";
import { Button } from "@patternfly/react-core";
import { Modal } from "@patternfly/react-core/deprecated";
import { AgentCard, AgentCardEditor } from "@app/components/agentCard";

export type EditAgentModalProps = {
    isOpen: boolean;
    agentCard: AgentCard;
    onClose: () => void;
    onSave: (updatedCard: AgentCard) => void;
};

export const EditAgentModal: FunctionComponent<EditAgentModalProps> = (props: EditAgentModalProps) => {
    const [editedCard, setEditedCard] = useState<AgentCard>(props.agentCard);

    useEffect(() => {
        if (props.isOpen) {
            setEditedCard(props.agentCard);
        }
    }, [props.isOpen]);

    const isValid = (): boolean => {
        return !!editedCard.name && editedCard.name.trim().length > 0;
    };

    return (
        <Modal
            title="Edit Agent Card"
            variant="large"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="edit-agent-modal pf-m-redhat-font"
            actions={[
                <Button
                    key="save"
                    variant="primary"
                    isDisabled={!isValid()}
                    onClick={() => props.onSave(editedCard)}
                >
                    Save as New Version
                </Button>,
                <Button key="cancel" variant="link" onClick={props.onClose}>
                    Cancel
                </Button>
            ]}
        >
            <AgentCardEditor
                agentCard={editedCard}
                onChange={setEditedCard}
            />
        </Modal>
    );
};
