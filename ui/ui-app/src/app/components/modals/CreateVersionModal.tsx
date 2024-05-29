import { FunctionComponent, useEffect, useState } from "react";
import { Button, FileUpload, Form, FormGroup, Modal, TextInput } from "@patternfly/react-core";
import { CreateVersion } from "@models/createVersion.model.ts";
import { isStringEmptyOrUndefined } from "@utils/string.utils.ts";
import { detectContentType } from "@utils/content.utils.ts";


/**
 * Labels
 */
export type CreateVersionModalProps = {
    artifactType: string;
    isOpen: boolean;
    onClose: () => void;
    onCreate: (data: CreateVersion) => void;
};

/**
 * Models the create version dialog.
 */
export const CreateVersionModal: FunctionComponent<CreateVersionModalProps> = (props: CreateVersionModalProps) => {
    const [version, setVersion] = useState("");
    const [content, setContent] = useState("");
    const [contentFilename] = useState("");
    const [contentIsLoading, setContentIsLoading] = useState(false);
    const [isFormValid, setFormValid] = useState(false);

    const onContentChange = (_event: any, value: any): void => {
        setContent(value);
    };

    const onFileReadStarted = (): void => {
        setContentIsLoading(true);
    };

    const onFileReadFinished = (): void => {
        setContentIsLoading(false);
    };

    const checkValid = (): void => {
        const newValid: boolean = isValid(content);
        setFormValid(newValid);
    };

    const isValid = (data: string): boolean => {
        return !!data;
    };

    const onCreate = (): void => {
        const data: CreateVersion = {
            version: isStringEmptyOrUndefined(version) ? undefined : version,
            content: {
                contentType: detectContentType(props.artifactType, content),
                content: content
            }
        };
        props.onCreate(data);
    };

    useEffect(() => {
        checkValid();
    }, [content]);

    return (
        <Modal
            title="Create Version"
            variant="large"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="create pf-m-redhat-font"
            actions={[
                <Button
                    key="create"
                    variant="primary"
                    data-testid="modal-btn-create"
                    onClick={onCreate}
                    isDisabled={!isFormValid}>Create</Button>,
                <Button
                    key="cancel"
                    variant="link"
                    data-testid="modal-btn-cancel"
                    onClick={props.onClose}>Cancel</Button>
            ]}
        >
            <Form>
                <FormGroup
                    label="Version"
                    isRequired={false}
                    fieldId="form-version"
                >
                    <TextInput
                        isRequired={false}
                        type="text"
                        id="form-version"
                        data-testid="create-version-version"
                        name="form-name"
                        aria-describedby="form-version-helper"
                        value={version}
                        placeholder="Version (optional)"
                        onChange={(_evt, value) => {
                            setVersion(value);
                        }}
                    />
                </FormGroup>
                <FormGroup
                    label="Content"
                    isRequired={true}
                    fieldId="form-content"
                >
                    <FileUpload
                        id="version-content"
                        data-testid="form-version-content"
                        type="text"
                        filename={contentFilename}
                        value={content}
                        isRequired={true}
                        allowEditingUploadedText={true}
                        onTextChange={onContentChange}
                        onDataChange={onContentChange}
                        onReadStarted={onFileReadStarted}
                        onReadFinished={onFileReadFinished}
                        onClearClick={() => onContentChange({}, "")}
                        isLoading={contentIsLoading}
                    />
                </FormGroup>
            </Form>
        </Modal>
    );
};
