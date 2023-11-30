import { FunctionComponent, useEffect, useState } from "react";
import { FileUpload, Form, FormGroup } from "@patternfly/react-core";


/**
 * Properties
 */
export type UploadVersionFormProps = {
    onValid: (valid: boolean) => void;
    onChange: (data: string) => void;
};

export const UploadVersionForm: FunctionComponent<UploadVersionFormProps> = (props: UploadVersionFormProps) => {
    const [content, setContent] = useState("");
    const [contentFilename] = useState("");
    const [contentIsLoading, setContentIsLoading] = useState(false);
    const [valid, setValid] = useState(false);
    
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
        const data: string = currentData();
        const newValid: boolean = isValid(data);
        setValid(newValid);
    };
    
    const isValid = (data: string): boolean => {
        return !!data;
    };
    
    const currentData = (): string => {
        return content;
    };
    
    const fireOnChange = (): void => {
        if (props.onChange) {
            props.onChange(currentData());
        }
    };
    
    const fireOnValid = (): void => {
        if (props.onValid) {
            props.onValid(valid);
        }
    };

    useEffect(() => {
        fireOnValid();
    }, [valid]);

    useEffect(() => {
        fireOnChange();
        checkValid();
    }, [content]);

    return (
        <Form>
            <FormGroup
                label="Artifact"
                isRequired={true}
                fieldId="form-artifact"
            >
                <FileUpload
                    id="artifact-content"
                    data-testid="form-upload"
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
    );
};

