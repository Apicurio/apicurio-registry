import { FunctionComponent } from "react";
import "./EditorModalContent.css";
import {
    Alert,
    Button
} from "@patternfly/react-core";
import {
    Modal
} from "@patternfly/react-core/deprecated";

export type DraftRecoveryModalProps = {
    isOpen: boolean;
    draftName: string;
    draftVersion?: string;
    savedOn?: number;
    onRestore: () => void;
    onDiscard: () => void;
};

export const DraftRecoveryModal: FunctionComponent<DraftRecoveryModalProps> = ({
    isOpen,
    draftName,
    draftVersion,
    savedOn,
    onRestore,
    onDiscard
}: DraftRecoveryModalProps) => {
    const snapshotSavedOn = savedOn ? new Date(savedOn).toLocaleString() : undefined;
    const draftLabel = draftVersion ? `${draftName} (version ${draftVersion})` : draftName;

    return (
        <Modal
            title="Restore unsaved draft changes?"
            variant="medium"
            isOpen={isOpen}
            onClose={() => undefined}
            showClose={false}
            actions={[
                <Button key="restore" variant="primary" data-testid="modal-btn-restore-draft"
                    onClick={onRestore}>Restore local changes</Button>,
                <Button key="discard" variant="link" data-testid="modal-btn-discard-draft"
                    onClick={onDiscard}>Discard local changes</Button>
            ]}
        >
            <p className="editor-modal-intro-text">
                Unsaved local changes were found for <b>{draftLabel}</b>.
            </p>
            <Alert
                variant="warning"
                isInline={true}
                title="These changes were stored locally in this browser before the editor page was reloaded or replaced."
            >
                {snapshotSavedOn ? <p>Local snapshot saved on {snapshotSavedOn}.</p> : undefined}
                <p>Restore them to continue editing, or discard them and keep the server version.</p>
            </Alert>
        </Modal>
    );
};
