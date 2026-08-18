import { FunctionComponent, useEffect, useState } from "react";
import { Button, FileUpload, Form, FormGroup } from "@patternfly/react-core";
import { Modal } from "@patternfly/react-core/deprecated";
import { detectContentType } from "@utils/content.utils.ts";
import { CreateVersion } from "@sdk/lib/generated-client/models";

export type TestVersionModalProps = {
    artifactType: string;
    isOpen: boolean;
    onClose: () => void;
    onTest: (data: CreateVersion) => void;
};

export const TestVersionModal: FunctionComponent<TestVersionModalProps> = (props: TestVersionModalProps) => {
    const [content, setContent] = useState("");
    const [contentIsLoading, setContentIsLoading] = useState(false);

    const onContentChange = (_event: any, value: any): void => setContent(value);

    const onTest = (): void => {
        props.onTest({
            content: {
                contentType: detectContentType(props.artifactType, content),
                content
            }
        });
    };

    useEffect(() => {
        if (props.isOpen) {
            setContent("");
        }
    }, [props.isOpen]);

    return (
        <Modal
            title="Test Content"
            variant="large"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="test-version pf-m-redhat-font"
            actions={[
                <Button key="test" variant="primary" data-testid="modal-btn-test"
                    onClick={onTest} isDisabled={!content}>Test</Button>,
                <Button key="cancel" variant="link" data-testid="modal-btn-cancel"
                    onClick={props.onClose}>Cancel</Button>
            ]}
        >
            <p className="modal-desc">
                Paste content below to check whether it would pass this artifact&apos;s configured
                content rules. Nothing is saved.
            </p>
            <Form>
                <FormGroup label="Content" isRequired={true} fieldId="form-content">
                    <FileUpload
                        id="test-version-content"
                        data-testid="form-test-version-content"
                        type="text"
                        value={content}
                        isRequired={true}
                        allowEditingUploadedText={true}
                        onTextChange={onContentChange}
                        onDataChange={onContentChange}
                        onReadStarted={() => setContentIsLoading(true)}
                        onReadFinished={() => setContentIsLoading(false)}
                        onClearClick={() => onContentChange({}, "")}
                        isLoading={contentIsLoading}
                    />
                </FormGroup>
            </Form>
        </Modal>
    );
};
