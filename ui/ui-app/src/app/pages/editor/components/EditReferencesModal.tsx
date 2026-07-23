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
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { detectContentType } from "@utils/content.utils.ts";

export type EditReferencesModalProps = {
    isOpen: boolean;
    references: ArtifactReference[];
    content: string;
    contentType: string;
    artifactType?: string;
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
    const [isDetecting, setIsDetecting] = useState(false);

    const groups: GroupsService = useGroupsService();

    useEffect(() => {
        if (props.isOpen) {
            setItems(toFormItems(props.references));
        }
    }, [props.isOpen, props.references]);

    const onDetectReferences = (): void => {
        if (!props.content) {
            return;
        }
        const contentType = props.contentType || detectContentType(props.artifactType, props.content);
        const artifactType = props.artifactType || undefined;
        setIsDetecting(true);
        groups.detectContentReferences(props.content, contentType, artifactType).then(refs => {
            const detected: ArtifactReferenceFormItem[] = refs.map(ref => ({
                name: ref.name || "",
                groupId: ref.groupId || "",
                artifactId: ref.artifactId || "",
                version: ref.version || ""
            }));
            setItems(current => {
                const existingNames = new Set(current.map(item => item.name));
                const newOnes = detected.filter(item => !existingNames.has(item.name));
                return [...current, ...newOnes];
            });
        }).catch(error => {
            console.error("[EditReferencesModal] Failed to detect references:", error);
        }).finally(() => {
            setIsDetecting(false);
        });
    };

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
                    onDetect={onDetectReferences}
                    isDetecting={isDetecting}
                />
            </Form>
        </Modal>
    );
};
