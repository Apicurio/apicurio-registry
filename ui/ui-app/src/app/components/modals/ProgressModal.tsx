import { FunctionComponent } from "react";
import "./ProgressModal.css";
import { Modal, Progress } from "@patternfly/react-core";


/**
 * Properties
 */
export type ProgressModalProps = {
    title: string;
    isCloseable: boolean;
    message: string;
    isOpen: boolean;
    progress: number | undefined;
    onClose: () => void;
};

/**
 * Models the "progress" modal.  This is shown when the user performs an asynchronous operation
 * with trackable progress (by percentage).
 */
export const ProgressModal: FunctionComponent<ProgressModalProps> = (props: ProgressModalProps) => {
    return (
        <Modal
            title={props.title}
            variant="small"
            isOpen={props.isOpen}
            showClose={props.isCloseable}
            onClose={props.onClose}
            className="progress pf-m-redhat-font"
            aria-label="progress-modal"
        >
            <Progress title={props.message} value={props.progress} />
        </Modal>
    );

};
