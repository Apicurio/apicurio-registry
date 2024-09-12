import { FunctionComponent, useState } from "react";
import { Button, Form, FormGroup, Modal, TextArea, TextInput } from "@patternfly/react-core";
import { CreateBranch } from "@sdk/lib/generated-client/models";


/**
 * Labels
 */
export type CreateBranchModalProps = {
    isOpen: boolean;
    onClose: () => void;
    onCreate: (data: CreateBranch) => void;
};

/**
 * Models the create branch dialog.
 */
export const CreateBranchModal: FunctionComponent<CreateBranchModalProps> = (props: CreateBranchModalProps) => {
    const [branchId, setBranchId] = useState("");
    const [description, setDescription] = useState("");

    const onBranchIdChange = (_event: any, value: any): void => {
        setBranchId(value);
    };

    const onDescriptionChange = (_event: any, value: any): void => {
        setDescription(value);
    };

    const onCreate = (): void => {
        const data: CreateBranch = {
            branchId,
            description
        };
        props.onCreate(data);
    };

    return (
        <Modal
            title="Create Branch"
            variant="medium"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="create pf-m-redhat-font"
            actions={[
                <Button
                    key="create"
                    variant="primary"
                    data-testid="modal-btn-create"
                    onClick={onCreate}>Create</Button>,
                <Button
                    key="cancel"
                    variant="link"
                    data-testid="modal-btn-cancel"
                    onClick={props.onClose}>Cancel</Button>
            ]}
        >
            <Form>
                <FormGroup
                    label="Branch Id"
                    isRequired={false}
                    fieldId="form-branch-id"
                >
                    <TextInput
                        isRequired={false}
                        type="text"
                        id="form-branch-id"
                        data-testid="create-version-branch-id"
                        name="form-name"
                        aria-describedby="form-branch-id-helper"
                        value={branchId}
                        placeholder="Unique Id of the new branch"
                        onChange={onBranchIdChange}
                    />
                </FormGroup>
                <FormGroup
                    label="Description"
                    fieldId="form-description"
                >
                    <TextArea
                        isRequired={false}
                        id="form-description"
                        data-testid="create-branch-modal-description"
                        name="form-description"
                        aria-describedby="form-description-helper"
                        value={description}
                        placeholder="Description of the branch"
                        onChange={onDescriptionChange}
                    />
                </FormGroup>
            </Form>
        </Modal>
    );
};
