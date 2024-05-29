import { FunctionComponent, useEffect, useState } from "react";
import { Button, Form, FormGroup, Grid, GridItem, Modal, TextInput } from "@patternfly/react-core";
import { CreateGroup } from "@models/createGroup.model.ts";


/**
 * Properties
 */
export type CreateGroupModalProps = {
    isOpen: boolean;
    onClose: () => void;
    onCreate: (data: CreateGroup) => void;
};

/**
 * Models the Create Group modal dialog.
 */
export const CreateGroupModal: FunctionComponent<CreateGroupModalProps> = (props: CreateGroupModalProps) => {
    const [isFormValid, setFormValid] = useState<boolean>(false);
    const [groupId, setGroupId] = useState("");

    useEffect(() => {
        setFormValid(groupId !== null && groupId.trim().length > 0);
    }, [groupId]);

    const reset = (): void => {
        setFormValid(false);
        setGroupId("");
    };

    const fireCloseEvent = (): void => {
        props.onClose();
        reset();
    };

    const fireCreateEvent = (): void => {
        const data: CreateGroup = {
            groupId
        };
        props.onCreate(data);
        reset();
    };

    return (
        <Modal
            title="Create Group"
            variant="medium"
            isOpen={props.isOpen}
            onClose={fireCloseEvent}
            className="create-group-modal pf-m-redhat-font"
            actions={[
                <Button key="create" variant="primary" data-testid="create-group-modal-btn-create" onClick={fireCreateEvent} isDisabled={!isFormValid}>Create</Button>,
                <Button key="cancel" variant="link" data-testid="create-group-modal-btn-cancel" onClick={fireCloseEvent}>Cancel</Button>
            ]}
        >
            <Form>
                <Grid hasGutter md={6}>
                    <GridItem span={12}>
                        <FormGroup
                            label="Group ID"
                            fieldId="form-group"
                            isRequired={true}
                        >
                            <TextInput
                                isRequired={true}
                                type="text"
                                id="form-groupId"
                                data-testid="create-group-groupId"
                                name="form-groupId"
                                aria-describedby="form-name-helper"
                                value={groupId}
                                placeholder="Group identifier"
                                onChange={(_evt, value) => setGroupId(value)}
                            />
                        </FormGroup>
                    </GridItem>
                </Grid>
            </Form>
        </Modal>
    );

};
