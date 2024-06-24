import { FunctionComponent, useEffect, useState } from "react";
import "./CreateArtifactForm.css";
import {
    FileUpload,
    Form,
    FormGroup,
    FormHelperText,
    HelperText,
    HelperTextItem,
    Tab,
    Tabs,
    TabTitleText,
    TextInput
} from "@patternfly/react-core";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import { If, ObjectSelect, UrlUpload } from "@apicurio/common-ui-components";
import { UrlService, useUrlService } from "@services/useUrlService.ts";
import { ArtifactTypesService, useArtifactTypesService } from "@services/useArtifactTypesService.ts";
import { detectContentType } from "@utils/content.utils.ts";
import { ContentTypes } from "@models/contentTypes.model.ts";
import { isStringEmptyOrUndefined } from "@utils/string.utils.ts";
import { CreateArtifact } from "@sdk/lib/generated-client/models";

/**
 * Properties
 */
export type CreateArtifactFormProps = {
    groupId?: string;
    onValid: (valid: boolean) => void;
    onChange: (groupId: string|null, data: CreateArtifact) => void;
};

type ArtifactTypeItem = {
    value?: string;
    label?: string;
    isDivider?: boolean;
};

const DEFAULT_ARTIFACT_TYPE: ArtifactTypeItem = {
    value: "",
    label: "Auto-Detect"
};
const DIVIDER: ArtifactTypeItem = {
    isDivider: true
};

/**
 * Models the Create Artifact modal dialog.
 */
export const CreateArtifactForm: FunctionComponent<CreateArtifactFormProps> = (props: CreateArtifactFormProps) => {
    const [content, setContent] = useState<string>();
    const [contentType, setContentType] = useState(ContentTypes.APPLICATION_JSON);
    const [contentIsLoading, setContentIsLoading] = useState(false);
    const [artifactId, setArtifactId] = useState("");
    const [groupId, setGroupId] = useState("");
    const [artifactType, setArtifactType] = useState("");
    const [tabKey, setTabKey] = useState(0);
    const [isFormValid, setFormValid] = useState(false);
    const [isArtifactIdValid, setArtifactIdValid] = useState(true);
    const [isGroupIdValid, setGroupIdValid] = useState(true);
    const [artifactTypes, setArtifactTypes] = useState<any[]>([]);
    const [artifactTypeOptions, setArtifactTypeOptions] = useState<ArtifactTypeItem[]>([]);
    const [selectedType, setSelectedType] = useState<ArtifactTypeItem>(DEFAULT_ARTIFACT_TYPE);

    const urlService: UrlService = useUrlService();
    const atService: ArtifactTypesService = useArtifactTypesService();

    const onFileTextChange = (_event: any, value: string | undefined): void => {
        setContent(value);
        setContentType(detectContentType(artifactType, value as string));
    };

    const onFileClear = (): void => {
        onFileTextChange(null, "");
    };

    const onFileReadStarted = (): void => {
        setContentIsLoading(true);
    };

    const onFileReadFinished = (): void => {
        setContentIsLoading(false);
    };

    const checkIdValid = (id: string|null): boolean => {
        if (!id) {
            //id is optional, server can generate it
            return true;
        } else {
            // character % breaks the ui
            const isAscii = (str: string) => {
                for (let i = 0; i < str.length; i++){
                    if(str.charCodeAt(i)>127){
                        return false;
                    }
                }
                return true;
            };
            return id.indexOf("%") == -1 && isAscii(id);
        }
    };

    const artifactIdValidated = (): any => {
        if (isArtifactIdValid) {
            if (!artifactId) {
                return "default";
            }
            return "success";
        } else {
            return "error";
        }
    };

    const groupValidated = (): any => {
        if (isGroupIdValid) {
            if (!groupId) {
                return "default";
            }
            return "success";
        } else {
            return "error";
        }
    };

    const fireOnChange = (): void => {
        const data: CreateArtifact = {
            artifactId,
            artifactType
        };
        if (!isStringEmptyOrUndefined(content)) {
            data.firstVersion = {
                content: {
                    contentType: contentType,
                    content: content as string
                }
            };
        }
        const gid: string|null = groupId === "" ? null : groupId;
        props.onChange(gid, data);
    };

    useEffect(() => {
        atService.allTypesWithLabels().then(setArtifactTypes);
    }, []);

    useEffect(() => {
        setContentType(detectContentType(artifactType, content as string));
    }, [content]);

    useEffect(() => {
        const items: ArtifactTypeItem[] = artifactTypes.map(item => ({
            value: item.id,
            label: item.label
        }));
        setArtifactTypeOptions([
            DEFAULT_ARTIFACT_TYPE,
            DIVIDER,
            ...items
        ]);
    }, [artifactTypes]);

    useEffect(() => {
        setArtifactType(selectedType.value as string);
    }, [selectedType]);

    useEffect(() => {
        const artifactIdValid: boolean = checkIdValid(artifactId);
        const groupIdValid: boolean = checkIdValid(groupId);

        setArtifactIdValid(artifactIdValid);
        setGroupIdValid(groupIdValid);
        let valid: boolean = artifactIdValid && groupIdValid;

        // Note: content can be empty, but if it is then an artifact type MUST be provided (since we cannot detect it from the content).
        if (isStringEmptyOrUndefined(content) && isStringEmptyOrUndefined(artifactType)) {
            valid = false;
        }

        setFormValid(valid);
        fireOnChange();
    }, [artifactType, artifactId, groupId, content]);

    useEffect(() => {
        if (props.onValid) {
            props.onValid(isFormValid);
        }
    }, [isFormValid]);

    return (
        <Form>
            <FormGroup
                label="Group Id / Artifact Id"
                fieldId="form-id"
            >
                <div className="group-and-id">
                    <TextInput
                        className="group"
                        isRequired={false}
                        type="text"
                        id="form-group"
                        data-testid="create-artifact-form-group"
                        name="form-group"
                        aria-describedby="form-group-helper"
                        value={props.groupId || groupId}
                        placeholder="Group Id"
                        isDisabled={props.groupId !== undefined}
                        onChange={(_evt, value) => setGroupId(value)}
                        validated={groupValidated()}
                    />
                    <span className="separator">/</span>
                    <TextInput
                        className="artifact-id"
                        isRequired={false}
                        type="text"
                        id="form-id"
                        data-testid="create-artifact-form-id"
                        name="form-id"
                        aria-describedby="form-id-helper"
                        value={artifactId}
                        placeholder="Artifact Id"
                        onChange={(_evt, value) => setArtifactId(value)}
                        validated={artifactIdValidated()}
                    />
                </div>
                <If condition={!isArtifactIdValid || !isGroupIdValid}>
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem variant="error" icon={ <ExclamationCircleIcon /> }>Character % and non ASCII characters are not allowed</HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </If>
                <FormHelperText>
                    <HelperText>
                        <HelperTextItem>(Optional) Group Id and Artifact Id are optional.  If Artifact Id is left blank, the server will generate one for you.</HelperTextItem>
                    </HelperText>
                </FormHelperText>
            </FormGroup>
            <FormGroup
                label="Type"
                fieldId="form-artifact-type"
                isRequired={true}
            >
                <div>
                    <ObjectSelect
                        value={selectedType}
                        items={artifactTypeOptions}
                        testId="create-artifact-form-type-select"
                        onSelect={setSelectedType}
                        itemIsDivider={(item) => item.isDivider}
                        itemToTestId={(item) => `create-artifact-form-${item.value}`}
                        itemToString={(item) => item.label} />
                </div>
            </FormGroup>
            <FormGroup
                label="Content (optional)"
                isRequired={false}
                fieldId="form-content"
            >
                <Tabs
                    className="create-tabs"
                    style={{ marginBottom: "8px" }}
                    activeKey={tabKey}
                    onSelect={(_event, eventKey) => {
                        setTabKey(eventKey as number);
                        onFileTextChange( null, undefined);
                        _event.preventDefault();
                        _event.stopPropagation();
                    }}
                    isBox={false}
                    role="region"
                >
                    <Tab
                        eventKey={0}
                        data-testid="create-artifact-from-file"
                        title={<TabTitleText>From file</TabTitleText>}
                        aria-label="Upload from file"
                    >
                        <FileUpload
                            id="artifact-content"
                            data-testid="create-artifact-form-file-upload"
                            type="text"
                            value={content!}
                            isRequired={false}
                            allowEditingUploadedText={true}
                            onDataChange={onFileTextChange}
                            onTextChange={onFileTextChange}
                            onClearClick={onFileClear}
                            onReadStarted={onFileReadStarted}
                            onReadFinished={onFileReadFinished}
                            isLoading={contentIsLoading}
                        />
                    </Tab>
                    <Tab
                        eventKey={1}
                        data-testid="create-artifact-from-url"
                        title={<TabTitleText>From URL</TabTitleText>}
                        aria-label="Upload from URL"
                    >
                        <UrlUpload
                            id="artifact-content-url"
                            urlPlaceholder="Enter a valid and accessible URL"
                            testId="create-artifact-form-url-upload"
                            onChange={(value) => {
                                onFileTextChange(null, value);
                            }}
                            onUrlFetch={(url) => urlService.fetchUrlContent(url)}
                        />
                    </Tab>
                </Tabs>
            </FormGroup>
        </Form>
    );

};
