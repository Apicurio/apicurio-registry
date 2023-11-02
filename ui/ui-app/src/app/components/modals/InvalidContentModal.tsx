import React, { FunctionComponent } from "react";
import "./InvalidContentModal.css";
import {
    Button,
    DataList,
    DataListCell,
    DataListItemCells,
    DataListItemRow,
    Modal,
    ModalVariant
} from "@patternfly/react-core";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import { ApiError } from "@models/apiError.model.ts";


/**
 * Properties
 */
export type InvalidContentModalProps = {
    error: ApiError|undefined;
    isOpen: boolean;
    onClose: () => void;
};

/**
 * Models the "invalid content" modal.  This is shown when the user tries to upload content
 * that is not valid.
 */
export const InvalidContentModal: FunctionComponent<InvalidContentModalProps> = (props: InvalidContentModalProps) => {

    const errorDetail = (): React.ReactElement => {
        if (props.error) {
            if (props.error.name === "RuleViolationException" && props.error.causes != null && props.error.causes.length > 0 ) {
                return (
                    <DataList aria-label="Error causes" className="error-causes" >
                        {
                            props.error.causes.map( (cause, idx) =>
                                <DataListItemRow key={""+idx} className="error-causes-item">
                                    <DataListItemCells
                                        dataListCells={[
                                            <DataListCell key="error icon" className="type-icon-cell">
                                                <ExclamationCircleIcon/>
                                            </DataListCell>,
                                            <DataListCell key="main content">
                                                <div className="error-causes-item-title">
                                                    <span>{cause.context != null ? (<b>{cause.context}</b>) : cause.description}</span>
                                                </div>
                                                <div className="error-causes-item-description">{cause.context != null ? cause.description : cause.context }</div>
                                            </DataListCell>
                                        ]}
                                    />
                                </DataListItemRow>
                            )
                        }
                    </DataList>
                );
            } else if (props.error.detail) {
                return (
                    <pre className="error-detail">
                        {props.error.detail}
                    </pre>
                );
            }
        }
        return <p/>;
    };
    
    return (
        <Modal
            title="Invalid Content Error"
            variant={ModalVariant.large}
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="edit-artifact-metaData pf-m-redhat-font"
            actions={[
                <Button key="close" variant="link" data-testid="modal-btn-close" onClick={props.onClose}>Close</Button>
            ]}
        >
            <p className="modal-desc" >The content you attempted to upload violated one or more of the established content rules.</p>
            { errorDetail() }
        </Modal>
    );

};
