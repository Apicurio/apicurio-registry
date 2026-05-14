import { FunctionComponent } from "react";
import "./EditorModalContent.css";
import { Alert, Button } from "@patternfly/react-core";
import { Modal } from "@patternfly/react-core/deprecated";

export type ReauthenticateModalProps = {
    isOpen: boolean;
    isRedirecting: boolean;
    confirmWithoutSnapshot: boolean;
    onConfirm: () => void;
    onClose: () => void;
};

export const ReauthenticateModal: FunctionComponent<ReauthenticateModalProps> = ({
    isOpen,
    isRedirecting,
    confirmWithoutSnapshot,
    onConfirm,
    onClose
}: ReauthenticateModalProps) => {
    return (
        <Modal
            title="Authentication required"
            variant="medium"
            isOpen={isOpen}
            onClose={onClose}
            actions={[
                <Button key="reauthenticate" variant={confirmWithoutSnapshot ? "danger" : "primary"} data-testid="modal-btn-reauthenticate"
                    isLoading={isRedirecting} onClick={onConfirm}>
                    {confirmWithoutSnapshot ? "Discard changes and sign in again" : "Sign in again"}
                </Button>,
                <Button key="cancel" variant="link" data-testid="modal-btn-dismiss-reauthentication"
                    isDisabled={isRedirecting} onClick={onClose}>Close</Button>
            ]}
        >
            <p className="editor-modal-intro-text">
                Your authentication session has expired. Sign in again to continue editing.
            </p>
            {confirmWithoutSnapshot ? (
                <Alert
                    variant="danger"
                    isInline={true}
                    title="The local recovery snapshot could not be saved because this browser has no storage space left."
                    className="editor-modal-followup-alert"
                >
                    <p>Before signing in again, copy the current content from the editor or Source view into a local file so you can restore it manually afterwards.</p>
                </Alert>
            ) : (
                <Alert
                    variant="info"
                    isInline={true}
                    title="Your unsaved draft changes will be preserved locally in this browser."
                >
                    <p>After you sign in again, the editor will offer to restore those changes.</p>
                </Alert>
            )}
        </Modal>
    );
};
