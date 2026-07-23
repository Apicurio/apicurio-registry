import { FunctionComponent, useEffect, useState } from "react";
import {
    Button,
    Form
} from "@patternfly/react-core";
import {
    Modal
} from "@patternfly/react-core/deprecated";
import {
    ArtifactReferenceFormItem,
    ReferencesFormGroup,
    formItemsToReferences,
    isReferencesValid
} from "@app/components";
import { ArtifactReference } from "@sdk/lib/generated-client/models";

export type EditReferencesModalProps = {
    isOpen: boolean;
    references: ArtifactReference[];
    onClose: () => void;
    onConfirm: (references: ArtifactReference[]) => void;
};

const toFormItems = (references: ArtifactReference[]): ArtifactReferenceFormItem[] => {
    return references.map(ref => ({
        name: ref.name || "",
        groupId: ref.groupId || "",
        artifactId: ref.artifactId || "",
        version: ref.version || ""
    }));
};

// Prop-driven: receives references from the parent and reports edits back via onConfirm; does not fetch on its own.
export const EditReferencesModal: FunctionComponent<EditReferencesModalProps> = (props: EditReferencesModalProps) => {
    const [items, setItems] = useState<ArtifactReferenceFormItem[]>([]);

    useEffect(() => {
        if (props.isOpen) {
            setItems(toFormItems(props.references));
        }
    }, [props.isOpen]);

    const onSave = (): void => {
        props.onConfirm(formItemsToReferences(items) ?? []);
        props.onClose();
    };

    return (
        <Modal
            title="Edit references"
            variant="medium"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="edit-references-modal pf-m-redhat-font"
            actions={[
                <Button
                    key="save"
                    variant="primary"
                    data-testid="modal-btn-save"
                    onClick={onSave}
                    isDisabled={!isReferencesValid(items)}
                >
                    Save
                </Button>,
                <Button
                    key="cancel"
                    variant="link"
                    data-testid="modal-btn-cancel"
                    onClick={props.onClose}
                >
                    Cancel
                </Button>
            ]}
        >
            <Form>
                <ReferencesFormGroup
                    references={items}
                    onChange={setItems}
                />
            </Form>
        </Modal>
    );
};
