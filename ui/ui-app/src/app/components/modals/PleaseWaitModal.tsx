import { FunctionComponent } from "react";
import "./PleaseWaitModal.css";
import { Modal, Spinner } from "@patternfly/react-core";


/**
 * Properties
 */
export type PleaseWaitModalProps = {
    message: string;
    isOpen: boolean;
};

/**
 * Models the "please wait" modal.  This is shown when the user performs an asynchronous operation.
 */
export const PleaseWaitModal: FunctionComponent<PleaseWaitModalProps> = (props: PleaseWaitModalProps) => {

    return (
        <Modal
            title="Please Wait"
            variant="small"
            isOpen={props.isOpen}
            header={<a href="#" />}
            showClose={false}
            className="please-wait pf-m-redhat-font"
            aria-label="please-wait-modal"
        >
            <Spinner size="md" className="spinner" />
            <span className="message">{ props.message }</span>
        </Modal>
    );

};
