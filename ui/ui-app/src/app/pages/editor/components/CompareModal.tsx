import { FunctionComponent, useEffect, useState } from "react";
import "./CompareModal.css";
import {
    Modal
} from "@patternfly/react-core/deprecated";
import { contentToString } from "@utils/content.utils.ts";
import { DiffView } from "@app/components";

/**
 * Properties
 */
export type CompareModalProps = {
    isOpen: boolean|undefined;
    before: any;
    beforeName: string;
    after: any;
    afterName: string;
    onClose: () => void;
};

export const CompareModal: FunctionComponent<CompareModalProps> = ({ isOpen, onClose, before, beforeName, after, afterName }: CompareModalProps) => {
    const [beforeAsString, setBeforeAsString] = useState<string>();
    const [afterAsString, setAfterAsString] = useState<string>();

    useEffect(() => {
        setBeforeAsString(contentToString(before));
    }, [before]);

    useEffect(() => {
        setAfterAsString(contentToString(after));
    }, [after]);

    return (
        <Modal id="compare-modal"
            title="Unsaved changes"
            isOpen={isOpen}
            onClose={onClose}>
            <div className="compare-modal-content">
                <DiffView
                    original={beforeAsString || ""}
                    originalLabel={`Original: ${beforeName}`}
                    modified={afterAsString || ""}
                    modifiedLabel={`Modified: ${afterName}`}
                    originalAriaLabel="Original"
                    modifiedAriaLabel="Modified"
                />
            </div>
        </Modal>
    );
};
