import { FunctionComponent, useEffect, useRef, useState } from "react";
import {
    Button,
    FileUpload,
    Form,
    FormGroup,
    FormHelperText,
    HelperText,
    HelperTextItem,
    TextInput
} from "@patternfly/react-core";
import {
    Modal
} from "@patternfly/react-core/deprecated";
import { If } from "@apicurio/common-ui-components";
import { isStringEmptyOrUndefined } from "@utils/string.utils.ts";
import { detectContentType, detectVersionInContent } from "@utils/content.utils.ts";
import { CreateVersion } from "@sdk/lib/generated-client/models";
import { ArtifactReferenceFormItem, ReferencesFormGroup, formItemsToReferences, isReferencesValid } from "@app/components";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";


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
    const [references, setReferences] = useState<ArtifactReferenceFormItem[]>([]);
    const [isDetectingRefs, setIsDetectingRefs] = useState(false);
    const [autoDetectedVersion, setAutoDetectedVersion] = useState<string | undefined>();
    // Ref rather than state: content change handlers can fire asynchronously (e.g. when
    // a file read finishes) and must see the current value, not a stale closure.
    const versionIsDirty = useRef(false);

    const groupsService: GroupsService = useGroupsService();

    const onContentChange = (_event: any, value: any): void => {
        // Auto-detect the version from the content (e.g. "info.version" in an OpenAPI or
        // AsyncAPI spec), but only while the user has not edited the version themselves.
        // When nothing is detected (non-spec content, or content that is transiently
        // unparseable mid-edit) the previous value is preserved rather than cleared.
        const detectedVersion: string | undefined = detectVersionInContent(value);
        if (!versionIsDirty.current && detectedVersion !== undefined) {
            setVersion(detectedVersion);
            setAutoDetectedVersion(detectedVersion);
        }
        setContent(value);
    };

    const onFileReadStarted = (): void => {
        setContentIsLoading(true);
    };

    const onFileReadFinished = (): void => {
        setContentIsLoading(false);
    };

    const checkValid = (): void => {
        const newValid: boolean = !!content && isReferencesValid(references);
        setFormValid(newValid);
    };

    const onCreate = (): void => {
        const data: CreateVersion = {
            version: isStringEmptyOrUndefined(version) ? undefined : version,
            content: {
                contentType: detectContentType(props.artifactType, content),
                content: content,
                references: formItemsToReferences(references)
            }
        };
        props.onCreate(data);
    };

    const onDetectReferences = (): void => {
        if (!content) {
            return;
        }
        const contentType = detectContentType(props.artifactType, content);
        const artifactType = props.artifactType || undefined;
        setIsDetectingRefs(true);
        groupsService.detectContentReferences(content, contentType, artifactType).then(refs => {
            const formItems: ArtifactReferenceFormItem[] = refs.map(ref => ({
                name: ref.name || "",
                groupId: ref.groupId || "",
                artifactId: ref.artifactId || "",
                version: ref.version || ""
            }));
            setReferences(formItems);
        }).catch(error => {
            console.error("[CreateVersionModal] Failed to detect references:", error);
        }).finally(() => {
            setIsDetectingRefs(false);
        });
    };

    useEffect(() => {
        if (props.isOpen) {
            setVersion("");
            setContent("");
            setReferences([]);
            setAutoDetectedVersion(undefined);
            versionIsDirty.current = false;
        }
    }, [props.isOpen]);

    useEffect(() => {
        checkValid();
    }, [content, references]);

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
                            // The user edited the version (including clearing it): stop
                            // auto-detecting.
                            versionIsDirty.current = true;
                            setAutoDetectedVersion(undefined);
                            setVersion(value);
                        }}
                    />
                    <If condition={autoDetectedVersion !== undefined && version === autoDetectedVersion}>
                        <FormHelperText>
                            <HelperText>
                                <HelperTextItem>Version detected from the content.</HelperTextItem>
                            </HelperText>
                        </FormHelperText>
                    </If>
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
                <ReferencesFormGroup
                    references={references}
                    onChange={setReferences}
                    onDetect={onDetectReferences}
                    isDetecting={isDetectingRefs}
                />
            </Form>
        </Modal>
    );
};
