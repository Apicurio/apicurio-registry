import { FunctionComponent } from "react";
import { Button } from "@patternfly/react-core";
import { Modal } from "@patternfly/react-core/deprecated";

export type TestVersionSuccessModalProps = {
    isOpen: boolean;
    onClose: () => void;
};

export const TestVersionSuccessModal: FunctionComponent<TestVersionSuccessModalProps> = (props) => (
    <Modal
        title="Content is valid"
        variant="small"
        isOpen={props.isOpen}
        onClose={props.onClose}
        className="test-version-success-modal pf-m-redhat-font"
        actions={[
            <Button key="close" variant="link" data-testid="modal-btn-close"
                onClick={props.onClose}>Close</Button>
        ]}
    >
        <p>The content passed all configured content rules for this artifact.</p>
    </Modal>
);
